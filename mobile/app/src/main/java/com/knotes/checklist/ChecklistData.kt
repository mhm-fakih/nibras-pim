package com.knotes.checklist

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/**
 * The checklists as rows — categories (the groups), items, and one check per item and day — the
 * same rows the backend keeps in its database (`checklist_category`, `checklist_item`,
 * `checklist_check`), so the phone and the web can sync them both ways.
 *
 * This replaced one Markdown file per week as the record. The files only reached the server through
 * folder sync; rows travel over the API, carry a note per tick, and say when an item stopped being
 * on the list instead of each week holding its own copy of the list.
 *
 * The screen still works on a [ChecklistWeek]: [project] builds one from the rows, and [applyWeek]
 * turns whatever the screen edited (a tick, a note, a rename, a whole "Edit as text" outline) back
 * into row changes by comparing it with that projection. So the week model and its tests stay the
 * single description of what an edit means.
 *
 * ## Weeks
 * Dates are week-aligned, as on the server: a category or item added while week W is on screen
 * starts on W's Monday; one removed while W is on screen ends on the Sunday before it. A week shows
 * whatever is active on any of its days. Re-adding a code that a category already had reopens that
 * row (its old ticks with it) rather than making a second one. A weekly category's one tick per week
 * is the check on its Monday.
 *
 * ## Sync bookkeeping
 * [Row.rev] is the server revision the phone last saw for a row (0 = never sent). [Row.dirty] is 0
 * for a row with nothing to send, else the value of a local counter at its last change — so a sync
 * can tell "changed again while the push was in flight" from "sent and acknowledged".
 *
 * Pure Kotlin (org.json aside), so all of it is pinned by plain unit tests.
 */
class ChecklistData {

    sealed interface Row {
        val uid: String
        val rev: Long
        val dirty: Long
    }

    data class Category(
        override val uid: String,
        val code: Int,
        val name: String,
        val weekly: Boolean,
        val start: LocalDate,
        val end: LocalDate?,
        override val rev: Long = 0,
        override val dirty: Long = 0,
    ) : Row

    data class Item(
        override val uid: String,
        val category: String,
        val code: Int,
        val name: String,
        val start: LocalDate,
        val end: LocalDate?,
        override val rev: Long = 0,
        override val dirty: Long = 0,
    ) : Row

    /** A cleared check (no mark, no note) is kept as a row: the clearing has to sync too. */
    data class Check(
        override val uid: String,
        val item: String,
        val day: LocalDate,
        val mark: Mark,
        val note: String,
        override val rev: Long = 0,
        override val dirty: Long = 0,
    ) : Row

    val categories = LinkedHashMap<String, Category>()
    val items = LinkedHashMap<String, Item>()
    val checks = LinkedHashMap<String, Check>()

    /** The server revision everything up to which this copy has seen. */
    var cursor: Long = 0

    /** Local change counter; see the class comment. */
    var counter: Long = 0
        private set

    /** Whether the old week files have been read in (see [importWeeks]); kept with the rows. */
    var filesImported: Boolean = false

    /** Overridable so tests get predictable uids. */
    var newUid: () -> String = { UUID.randomUUID().toString() }

    val isEmpty: Boolean get() = categories.isEmpty()

    // ------------------------------------------------------------------ reading

    /** The week [id] as the screen shows it; empty groups list when nothing is active in it. */
    fun project(id: WeekId): ChecklistWeek {
        val monday = id.monday
        val byItem = checksByItem(monday, id.sunday)
        val sections = categories.values
            .filter { active(it.start, it.end, monday) }
            .sortedBy { it.code }
            .map { c ->
                val width = if (c.weekly) 1 else DAYS
                val rows = items.values
                    .filter { it.category == c.uid && active(it.start, it.end, monday) }
                    .sortedBy { it.code }
                    .map { i ->
                        val marks = MutableList(width) { Mark.NONE }
                        val notes = MutableList(width) { "" }
                        byItem[i.uid].orEmpty().forEach { k ->
                            val slot = if (c.weekly) 0 else (k.day.toEpochDay() - monday.toEpochDay()).toInt()
                            if (c.weekly && k.day != monday) return@forEach
                            if (slot in 0 until width) {
                                marks[slot] = k.mark
                                notes[slot] = k.note
                            }
                        }
                        ChecklistItem(i.code, i.name, marks, if (notes.all { it.isEmpty() }) emptyList() else notes)
                    }
                ChecklistSection(c.code, c.name, c.weekly, rows)
            }
        return ChecklistWeek(id, sections)
    }

    /** Whether anything was ticked or written down in week [id] — the "not started" label's test. */
    fun hasEntries(id: WeekId): Boolean = checks.values.any {
        !it.day.isBefore(id.monday) && !it.day.isAfter(id.sunday) && (it.mark != Mark.NONE || it.note.isNotBlank())
    }

    private fun checksByItem(from: LocalDate, to: LocalDate): Map<String, List<Check>> =
        checks.values.filter { !it.day.isBefore(from) && !it.day.isAfter(to) }.groupBy { it.item }

    // ------------------------------------------------------------------ writing

    /**
     * Records [edited] — the screen's copy of week [ChecklistWeek.id] after an edit — as row
     * changes against what [project] gives for that week now. Returns whether anything changed.
     */
    fun applyWeek(edited: ChecklistWeek): Boolean {
        val before = counter
        val id = edited.id
        val monday = id.monday
        val base = project(id)
        val baseSections = base.sections.associateBy { it.code }

        for (s in edited.sections) {
            val cat = categoryFor(s, monday)
            val baseSection = baseSections[s.code]
            val baseItems = baseSection?.items?.associateBy { it.code }.orEmpty()
            for (item in s.items) {
                val row = itemFor(cat.uid, item, monday)
                val was = baseItems[item.code]?.takeIf { baseSection?.weekly == s.weekly }
                for (slot in item.marks.indices) {
                    val mark = item.marks[slot]
                    val note = item.noteAt(slot).trim()
                    val oldMark = was?.marks?.getOrNull(slot) ?: Mark.NONE
                    val oldNote = was?.noteAt(slot)?.trim().orEmpty()
                    if (mark == oldMark && note == oldNote) continue
                    val day = if (s.weekly) monday else monday.plusDays(slot.toLong())
                    setCheck(row.uid, day, mark, note)
                }
            }
            // Items the edit took off this week's list end before it.
            baseSection?.items?.filter { b -> s.items.none { it.code == b.code } }?.forEach { b ->
                items.values.firstOrNull { it.category == cat.uid && it.code == b.code && active(it.start, it.end, monday) }
                    ?.let { put(it.copy(end = monday.minusDays(1))) }
            }
        }
        // Groups the edit dropped end before this week, and so do their items.
        base.sections.filter { b -> edited.sections.none { it.code == b.code } }.forEach { b ->
            val cat = categories.values.firstOrNull { it.code == b.code && active(it.start, it.end, monday) } ?: return@forEach
            put(cat.copy(end = monday.minusDays(1)))
            items.values.filter { it.category == cat.uid && active(it.start, it.end, monday) }
                .forEach { put(it.copy(end = monday.minusDays(1))) }
        }
        return counter != before
    }

    /** The category for group [s] in the week of [monday]: found by code (reopened if ended), else new. */
    private fun categoryFor(s: ChecklistSection, monday: LocalDate): Category {
        val existing = categories.values.filter { it.code == s.code }
            .sortedWith(compareByDescending<Category> { active(it.start, it.end, monday) }.thenByDescending { it.start })
            .firstOrNull()
        if (existing == null) {
            return put(Category(newUid(), s.code, s.title, s.weekly, monday, null))
        }
        var next = existing.copy(name = s.title, weekly = s.weekly)
        if (!active(existing.start, existing.end, monday)) {
            next = next.copy(end = null, start = minOf(existing.start, monday))
        }
        return if (next != existing) put(next) else existing
    }

    private fun itemFor(category: String, item: ChecklistItem, monday: LocalDate): Item {
        val existing = items.values.filter { it.category == category && it.code == item.code }
            .sortedWith(compareByDescending<Item> { active(it.start, it.end, monday) }.thenByDescending { it.start })
            .firstOrNull()
        if (existing == null) {
            return put(Item(newUid(), category, item.code, item.text, monday, null))
        }
        var next = existing.copy(name = item.text)
        if (!active(existing.start, existing.end, monday)) {
            next = next.copy(end = null, start = minOf(existing.start, monday))
        }
        return if (next != existing) put(next) else existing
    }

    private fun setCheck(itemUid: String, day: LocalDate, mark: Mark, note: String) {
        val uid = checkUid(itemUid, day)
        val cur = checks[uid]
        if (cur != null && cur.mark == mark && cur.note == note) return
        put(Check(uid, itemUid, day, mark, note, rev = cur?.rev ?: 0))
    }

    /** Stores [row] as a local change: stamped dirty with the next counter value. */
    private fun <T : Row> put(row: T): T {
        counter++
        @Suppress("UNCHECKED_CAST")
        val stamped = when (row) {
            is Category -> row.copy(dirty = counter)
            is Item -> row.copy(dirty = counter)
            is Check -> row.copy(dirty = counter)
            else -> row
        } as T
        store(stamped)
        return stamped
    }

    private fun store(row: Row) {
        when (row) {
            is Category -> categories[row.uid] = row
            is Item -> items[row.uid] = row
            is Check -> checks[row.uid] = row
        }
    }

    // ------------------------------------------------------------------ importing the week files

    /**
     * Reads the old week files ([weeks], any order) into rows — once, when the phone first runs
     * with this store. The uids are the ones the server gives the same files ("c1", "i1.2",
     * "i1.2@2026-11-09"), so whichever side imports first, the other lands on the same rows.
     *
     * Each category and item takes its latest name, starts on the Monday of the first week it is
     * in and, if the latest week no longer has it, ends on the Sunday of the last week that did.
     * Every row is marked to be sent.
     */
    fun importWeeks(weeks: List<ChecklistWeek>) {
        val sorted = weeks.sortedBy { it.id }
        val latest = sorted.lastOrNull()?.id?.monday ?: return

        data class Span(val first: LocalDate, val last: LocalDate, val name: String, val weekly: Boolean)
        val cats = LinkedHashMap<Int, Span>()
        val its = LinkedHashMap<Pair<Int, Int>, Span>()
        for (w in sorted) for (s in w.sections) {
            val m = w.id.monday
            cats[s.code] = cats[s.code]?.copy(last = m, name = s.title, weekly = s.weekly) ?: Span(m, m, s.title, s.weekly)
            for (i in s.items) {
                val k = s.code to i.code
                its[k] = its[k]?.copy(last = m, name = i.text) ?: Span(m, m, i.text, s.weekly)
            }
        }
        fun endOf(s: Span) = if (s.last.isBefore(latest)) s.last.plusDays(6) else null
        cats.forEach { (code, s) ->
            val uid = "c$code"
            if (uid !in categories) put(Category(uid, code, s.name, s.weekly, s.first, endOf(s)))
        }
        its.forEach { (k, s) ->
            val uid = "i${k.first}.${k.second}"
            if (uid !in items) put(Item(uid, "c${k.first}", k.second, s.name, s.first, endOf(s)))
        }
        for (w in sorted) for (s in w.sections) for (i in s.items) {
            val itemUid = "i${s.code}.${i.code}"
            i.marks.forEachIndexed { slot, mark ->
                if (mark == Mark.NONE) return@forEachIndexed
                val day = w.id.monday.plusDays(if (s.weekly) 0L else slot.toLong())
                if (checkUid(itemUid, day) !in checks) put(Check(checkUid(itemUid, day), itemUid, day, mark, ""))
            }
        }
    }

    // ------------------------------------------------------------------ sync

    /** What a push sends: every dirty row, and the dirty stamps it had when it was sent. */
    class Outgoing(val body: JSONObject, val stamps: Map<String, Long>)

    /** The POST /api/checklists/sync body: dirty rows with the rev each was last seen at. */
    fun outgoing(): Outgoing {
        val stamps = HashMap<String, Long>()
        val cats = JSONArray()
        val its = JSONArray()
        val ks = JSONArray()
        categories.values.filter { it.dirty > 0 }.forEach {
            stamps["c:" + it.uid] = it.dirty
            cats.put(categoryJson(it).put("baseRev", it.rev))
        }
        items.values.filter { it.dirty > 0 }.forEach {
            stamps["i:" + it.uid] = it.dirty
            its.put(itemJson(it).put("baseRev", it.rev))
        }
        checks.values.filter { it.dirty > 0 }.forEach {
            stamps["k:" + it.uid] = it.dirty
            ks.put(checkJson(it).put("baseRev", it.rev))
        }
        val body = JSONObject().put("since", cursor).put("categories", cats).put("items", its).put("checks", ks)
        return Outgoing(body, stamps)
    }

    val hasOutgoing: Boolean
        get() = categories.values.any { it.dirty > 0 } || items.values.any { it.dirty > 0 } || checks.values.any { it.dirty > 0 }

    /**
     * Takes in the server's answer to a push (or a plain GET): every row it returns replaces the
     * local one — that is how a conflict the web won reaches the phone — unless the local row
     * changed again after the push was sent, in which case the local change stays to go next time
     * (with the server's rev, so it is not mistaken for a conflict). Rows that were sent and not
     * touched since are no longer dirty, whether or not they came back. The cursor moves to the
     * server's revision.
     */
    fun applyIncoming(response: JSONObject, sent: Map<String, Long> = emptyMap()) {
        fun keepLocal(key: String, local: Row?): Boolean =
            local != null && local.dirty > 0 && local.dirty != sent[key]

        response.optJSONArray("categories")?.let { arr ->
            for (n in 0 until arr.length()) {
                val r = parseCategory(arr.getJSONObject(n)) ?: continue
                val local = categories[r.uid]
                categories[r.uid] = if (keepLocal("c:" + r.uid, local)) local!!.copy(rev = r.rev) else r
            }
        }
        response.optJSONArray("items")?.let { arr ->
            for (n in 0 until arr.length()) {
                val r = parseItem(arr.getJSONObject(n)) ?: continue
                val local = items[r.uid]
                items[r.uid] = if (keepLocal("i:" + r.uid, local)) local!!.copy(rev = r.rev) else r
            }
        }
        response.optJSONArray("checks")?.let { arr ->
            for (n in 0 until arr.length()) {
                val r = parseCheck(arr.getJSONObject(n)) ?: continue
                val local = checks[r.uid]
                checks[r.uid] = if (keepLocal("k:" + r.uid, local)) local!!.copy(rev = r.rev) else r
            }
        }
        // Sent and not changed since: acknowledged, whatever the server made of it.
        sent.forEach { (key, stamp) ->
            val uid = key.substring(2)
            when (key[0]) {
                'c' -> categories[uid]?.takeIf { it.dirty == stamp }?.let { categories[uid] = it.copy(dirty = 0) }
                'i' -> items[uid]?.takeIf { it.dirty == stamp }?.let { items[uid] = it.copy(dirty = 0) }
                'k' -> checks[uid]?.takeIf { it.dirty == stamp }?.let { checks[uid] = it.copy(dirty = 0) }
            }
        }
        if (response.has("rev")) cursor = maxOf(cursor, response.optLong("rev"))
    }

    // ------------------------------------------------------------------ JSON (storage and wire)

    fun toJson(): JSONObject {
        val o = JSONObject().put("version", 1).put("cursor", cursor).put("counter", counter)
            .put("filesImported", filesImported)
        o.put("categories", JSONArray().also { a ->
            categories.values.forEach { a.put(categoryJson(it).put("rev", it.rev).put("dirty", it.dirty)) }
        })
        o.put("items", JSONArray().also { a ->
            items.values.forEach { a.put(itemJson(it).put("rev", it.rev).put("dirty", it.dirty)) }
        })
        o.put("checks", JSONArray().also { a ->
            checks.values.forEach { a.put(checkJson(it).put("rev", it.rev).put("dirty", it.dirty)) }
        })
        return o
    }

    companion object {
        fun checkUid(itemUid: String, day: LocalDate) = "$itemUid@$day"

        /** Active on any day of the week starting [monday]. */
        fun active(start: LocalDate, end: LocalDate?, monday: LocalDate): Boolean =
            !start.isAfter(monday.plusDays(6)) && (end == null || !end.isBefore(monday))

        fun fromJson(o: JSONObject): ChecklistData {
            val d = ChecklistData()
            d.cursor = o.optLong("cursor")
            d.counter = o.optLong("counter")
            d.filesImported = o.optBoolean("filesImported")
            fun dirty(x: JSONObject) = x.optLong("dirty")
            o.optJSONArray("categories")?.let { a ->
                for (n in 0 until a.length()) {
                    val x = a.getJSONObject(n)
                    parseCategory(x)?.let { d.categories[it.uid] = it.copy(dirty = dirty(x)) }
                }
            }
            o.optJSONArray("items")?.let { a ->
                for (n in 0 until a.length()) {
                    val x = a.getJSONObject(n)
                    parseItem(x)?.let { d.items[it.uid] = it.copy(dirty = dirty(x)) }
                }
            }
            o.optJSONArray("checks")?.let { a ->
                for (n in 0 until a.length()) {
                    val x = a.getJSONObject(n)
                    parseCheck(x)?.let { d.checks[it.uid] = it.copy(dirty = dirty(x)) }
                }
            }
            // A counter below a stored stamp would make a new edit look already sent.
            val maxStamp = (d.categories.values + d.items.values + d.checks.values).maxOfOrNull { it.dirty } ?: 0
            if (d.counter < maxStamp) d.counter = maxStamp
            return d
        }

        private fun categoryJson(c: Category) = JSONObject()
            .put("uid", c.uid).put("code", c.code).put("name", c.name).put("weekly", c.weekly)
            .put("start", c.start.toString()).put("end", c.end?.toString() ?: JSONObject.NULL)

        private fun itemJson(i: Item) = JSONObject()
            .put("uid", i.uid).put("category", i.category).put("code", i.code).put("name", i.name)
            .put("start", i.start.toString()).put("end", i.end?.toString() ?: JSONObject.NULL)

        private fun checkJson(k: Check) = JSONObject()
            .put("uid", k.uid).put("item", k.item).put("day", k.day.toString())
            .put("mark", k.mark.name).put("note", k.note.ifEmpty { null } ?: JSONObject.NULL)

        private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key, "").ifEmpty { null }

        private fun JSONObject.date(key: String): LocalDate? = str(key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        private fun parseCategory(x: JSONObject): Category? {
            val uid = x.str("uid") ?: return null
            return Category(uid, x.optInt("code"), x.str("name") ?: "", x.optBoolean("weekly"),
                x.date("start") ?: return null, x.date("end"), x.optLong("rev"))
        }

        private fun parseItem(x: JSONObject): Item? {
            val uid = x.str("uid") ?: return null
            return Item(uid, x.str("category") ?: return null, x.optInt("code"), x.str("name") ?: "",
                x.date("start") ?: return null, x.date("end"), x.optLong("rev"))
        }

        private fun parseCheck(x: JSONObject): Check? {
            val item = x.str("item") ?: return null
            val day = x.date("day") ?: return null
            val mark = runCatching { Mark.valueOf(x.str("mark") ?: "NONE") }.getOrDefault(Mark.NONE)
            return Check(x.str("uid") ?: checkUid(item, day), item, day, mark, x.str("note").orEmpty(), x.optLong("rev"))
        }
    }
}
