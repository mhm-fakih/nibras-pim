package com.knotes.checklist

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The checklist rows behind the screen: a week projected from them, an edited week turned back into
 * row changes, the one-time import of the old week files, and taking in a sync answer.
 */
class ChecklistDataTest {

    private val w46 = WeekId(2026, 46)          // Mon 2026-11-09
    private val mon = LocalDate.of(2026, 11, 9)

    private fun data(): ChecklistData = ChecklistData().also { d ->
        var n = 0
        d.newUid = { "u${++n}" }
    }

    /** A list with one daily item and one weekly item, saved in week 46. */
    private fun seeded(): ChecklistData = data().also { d ->
        val week = ChecklistWeek.starter(w46, listOf("Start of day"), "Weekly")
            .addItem(0, "Charge the phone")
            .addItem(1, "Call the family")
        assertTrue(d.applyWeek(week))
    }

    @Test
    fun `a saved week projects back unchanged and carries on to later weeks`() {
        val d = seeded()
        val back = d.project(w46)
        assertEquals(listOf("Start of day", "Weekly"), back.sections.map { it.title })
        assertEquals("Charge the phone", back.sections[0].items.single().text)
        assertEquals(1, back.sections[1].items.single().marks.size)
        // Items carry on by themselves, unticked; the week before the list began shows nothing.
        assertEquals(1, d.project(w46.plus(5)).sections[0].items.size)
        assertTrue(d.project(w46.plus(-1)).sections.isEmpty())
        assertEquals(mon, d.categories.values.first().start)
    }

    @Test
    fun `a tick and a note become one check on that day and saving the same week again changes nothing`() {
        val d = seeded()
        val ticked = d.project(w46).cycle(0, 0, 2)
            .setSlot(0, 0, 3, Mark.PARTIAL, "  half  ")
        assertTrue(d.applyWeek(ticked))
        val item = d.items.values.first { it.name == "Charge the phone" }
        assertEquals(Mark.DONE, d.checks["${item.uid}@${mon.plusDays(2)}"]!!.mark)
        val wed = d.checks["${item.uid}@${mon.plusDays(3)}"]!!
        assertEquals(Mark.PARTIAL, wed.mark)
        assertEquals("half", wed.note)
        assertEquals("half", d.project(w46).sections[0].items[0].noteAt(3))
        assertTrue(d.hasEntries(w46))
        assertFalse(d.hasEntries(w46.plus(1)))
        assertFalse(d.applyWeek(d.project(w46)))
    }

    @Test
    fun `a weekly item's tick is on its week's Monday`() {
        val d = seeded()
        d.applyWeek(d.project(w46).cycle(1, 0, 0))
        val item = d.items.values.first { it.name == "Call the family" }
        assertEquals(Mark.DONE, d.checks["${item.uid}@$mon"]!!.mark)
    }

    @Test
    fun `removing an item ends it before the week and adding its code back reopens it`() {
        val d = seeded()
        d.applyWeek(d.project(w46).cycle(0, 0, 0))
        val w48 = w46.plus(2)
        d.applyWeek(d.project(w48).removeItem(0, 0))
        val item = d.items.values.first { it.name == "Charge the phone" }
        assertEquals(w48.monday.minusDays(1), item.end)
        assertTrue(d.project(w48).sections[0].items.isEmpty())
        assertEquals(1, d.project(w46.plus(1)).sections[0].items.size)

        // The outline gives the code back: the same row comes back, ticks and all.
        d.applyWeek(d.project(w48).applyOutline("## 1 Start of day\n1.1 Charge the phone fully"))
        val again = d.items.values.filter { it.code == 1 && it.category == item.category }
        assertEquals(1, again.size)
        assertNull(again.single().end)
        assertEquals("Charge the phone fully", again.single().name)
        assertEquals(Mark.DONE, d.project(w46).sections[0].items[0].marks[0])
    }

    @Test
    fun `a new item gets the next code and starts on the week's Monday and renaming is one row`() {
        val d = seeded()
        val w47 = w46.plus(1)
        d.applyWeek(d.project(w47).addItem(0, "Review the plan").rename(0, 0, "Charge"))
        val added = d.items.values.first { it.name == "Review the plan" }
        assertEquals(2, added.code)
        assertEquals(w47.monday, added.start)
        assertTrue(d.project(w46).sections[0].items.none { it.text == "Review the plan" })
        assertEquals("Charge", d.project(w46).sections[0].items[0].text)    // one name, every week
    }

    @Test
    fun `a group dropped from the outline ends with its items while a new one starts and weekly is switchable`() {
        val d = seeded()
        val w47 = w46.plus(1)
        d.applyWeek(d.project(w47).applyOutline("## 1 Start of day [weekly]\n1.1 Charge the phone\n\n## Study\nRead"))
        val p = d.project(w47)
        assertEquals(listOf("Start of day", "Study"), p.sections.map { it.title })
        assertTrue(p.sections[0].weekly)
        assertEquals(3, p.sections[1].code)
        val weekly = d.categories.values.first { it.code == 2 }
        assertEquals(w47.monday.minusDays(1), weekly.end)
        assertEquals(w47.monday.minusDays(1), d.items.values.first { it.name == "Call the family" }.end)
    }

    @Test
    fun `the old week files import with the server's uids and week-aligned dates`() {
        val w44 = WeekId(2026, 44)
        val w45 = WeekId(2026, 45)
        val first = ChecklistWeek.starter(w44, listOf("Start of day"), "Weekly")
            .addItem(0, "Old name").addItem(0, "Dropped later").addItem(1, "Call")
            .cycle(0, 0, 1).cycle(1, 0, 0)
        val second = first.carriedTo(w45).rename(0, 0, "New name").removeItem(0, 1).cycle(0, 0, 6)
        val d = data()
        d.importWeeks(listOf(second, first))

        assertEquals(setOf("c1", "c2"), d.categories.keys)
        assertEquals("New name", d.items["i1.1"]!!.name)
        assertEquals(w44.monday, d.items["i1.1"]!!.start)
        assertNull(d.items["i1.1"]!!.end)
        assertEquals(w44.sunday, d.items["i1.2"]!!.end)
        assertEquals(Mark.DONE, d.checks["i1.1@${w44.monday.plusDays(1)}"]!!.mark)
        assertEquals(Mark.DONE, d.checks["i1.1@${w45.sunday}"]!!.mark)
        assertEquals(Mark.DONE, d.checks["i2.1@${w44.monday}"]!!.mark)
        assertTrue((d.categories.values + d.items.values + d.checks.values).all { it.dirty > 0 })

        // Running it again adds nothing.
        val before = d.counter
        d.importWeeks(listOf(first, second))
        assertEquals(before, d.counter)
    }

    @Test
    fun `a push carries every dirty row with the rev it was last seen at`() {
        val d = seeded()
        val out = d.outgoing()
        assertEquals(0L, out.body.getLong("since"))
        assertEquals(2, out.body.getJSONArray("categories").length())
        assertEquals(2, out.body.getJSONArray("items").length())
        val c = out.body.getJSONArray("categories").getJSONObject(0)
        assertEquals(0L, c.getLong("baseRev"))
        assertEquals("2026-11-09", c.getString("start"))
        assertTrue(c.isNull("end"))
    }

    @Test
    fun `the answer replaces rows and acknowledges what was sent but keeps what changed meanwhile`() {
        val d = seeded()
        d.applyWeek(d.project(w46).cycle(0, 0, 0))
        val item = d.items.values.first { it.name == "Charge the phone" }
        val out = d.outgoing()
        // An edit lands while the push is in flight.
        d.applyWeek(d.project(w46).setSlot(0, 0, 1, Mark.DONE, "meanwhile"))

        val answer = JSONObject().put("rev", 40)
            .put("categories", JSONArray())
            .put("items", JSONArray().put(JSONObject().put("uid", item.uid).put("category", item.category)
                .put("code", 1).put("name", "Renamed on the web").put("start", "2026-11-09").put("end", JSONObject.NULL).put("rev", 31)))
            .put("checks", JSONArray()
                .put(JSONObject().put("uid", "${item.uid}@$mon").put("item", item.uid).put("day", "$mon")
                    .put("mark", "PARTIAL").put("note", "web won").put("rev", 33))
                .put(JSONObject().put("uid", "${item.uid}@${mon.plusDays(1)}").put("item", item.uid)
                    .put("day", "${mon.plusDays(1)}").put("mark", "NONE").put("note", JSONObject.NULL).put("rev", 34)))
        d.applyIncoming(answer, out.stamps)

        assertEquals(40L, d.cursor)
        assertEquals("Renamed on the web", d.items[item.uid]!!.name)
        assertEquals(0L, d.items[item.uid]!!.dirty)
        val monday = d.checks["${item.uid}@$mon"]!!
        assertEquals(Mark.PARTIAL, monday.mark)            // the server's word on a sent row stands
        assertEquals("web won", monday.note)
        val tuesday = d.checks["${item.uid}@${mon.plusDays(1)}"]!!
        assertEquals("meanwhile", tuesday.note)            // changed after sending: kept, to go next
        assertEquals(34L, tuesday.rev)
        assertTrue(tuesday.dirty > 0)
        // Sent, not changed since, not in the answer: no longer pending.
        assertTrue(d.categories.values.all { it.dirty == 0L })
    }

    @Test
    fun `the store survives a JSON round trip`() {
        val d = seeded()
        d.applyWeek(d.project(w46).setSlot(0, 0, 0, Mark.DONE, "ملاحظة"))
        d.cursor = 12
        d.filesImported = true
        val back = ChecklistData.fromJson(JSONObject(d.toJson().toString()))
        assertEquals(d.categories, back.categories)
        assertEquals(d.items, back.items)
        assertEquals(d.checks, back.checks)
        assertEquals(12L, back.cursor)
        assertEquals(d.counter, back.counter)
        assertTrue(back.filesImported)
    }
}
