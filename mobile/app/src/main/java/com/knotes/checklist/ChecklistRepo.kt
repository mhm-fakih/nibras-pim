package com.knotes.checklist

import android.content.Context
import com.knotes.SettingsStore
import com.knotes.nibras.NibrasApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

/**
 * The phone's checklists: one [ChecklistData] for the whole process, kept in the app's private
 * storage (`checklists.json`) rather than in the shared notes folder — it reaches the computer
 * through [ChecklistSync], not folder sync.
 *
 * An object, so the screen, the reminders and the sync all look at one copy: two copies would each
 * save over the other's changes. Every access goes through [lock]; file IO happens under it, so
 * call these off the main thread.
 *
 * ## The old week files
 * The first time this runs, the week files in the checklists folder are read in (see
 * [ChecklistData.importWeeks]). They are only read, never changed or deleted. If the folder can't
 * be read yet (storage permission not granted), the import is tried again on a later start.
 */
object ChecklistRepo {

    private val lock = Any()
    private var data: ChecklistData? = null

    private val _version = MutableStateFlow(0L)

    /** Moves on every change, local or synced in, so an open screen knows to re-read its week. */
    val version: StateFlow<Long> = _version.asStateFlow()

    private fun file(context: Context) = File(context.applicationContext.filesDir, "checklists.json")

    /** Runs [f] on the loaded rows, under the lock. */
    fun <T> read(context: Context, f: (ChecklistData) -> T): T = synchronized(lock) { f(load(context)) }

    /** Runs [f] on the rows under the lock and saves them if it says something changed. */
    fun write(context: Context, f: (ChecklistData) -> Boolean): Boolean = synchronized(lock) {
        val d = load(context)
        val changed = f(d)
        if (changed) {
            persist(context, d)
            _version.value++
        }
        changed
    }

    /**
     * The week to show: the rows' projection, or — on a phone with no checklists at all yet — the
     * starter groups, which become rows on the first edit. The flag says whether anything was
     * ticked or noted in the week (the "not started" label).
     */
    fun open(context: Context, id: WeekId, starter: () -> ChecklistWeek): Pair<ChecklistWeek, Boolean> =
        read(context) { d -> if (d.isEmpty) starter() to false else d.project(id) to d.hasEntries(id) }

    /** Records an edited week and, when the server is set up, sends it a few seconds later. */
    fun save(context: Context, week: ChecklistWeek): Boolean {
        val changed = write(context) { it.applyWeek(week) }
        if (changed) ChecklistSync.schedule(context)
        return changed
    }

    private fun load(context: Context): ChecklistData {
        data?.let { return it }
        val f = file(context)
        val d = if (f.isFile) {
            runCatching { ChecklistData.fromJson(JSONObject(f.readText())) }.getOrElse {
                // Never start over silently on top of a file we failed to read: keep it aside.
                f.copyTo(File(f.parentFile, "checklists.unreadable-${System.currentTimeMillis()}.json"), overwrite = true)
                ChecklistData()
            }
        } else ChecklistData()
        if (!d.filesImported) importFiles(context, d)
        data = d
        return d
    }

    private fun importFiles(context: Context, d: ChecklistData) {
        val folder = File(SettingsStore(context.applicationContext).value.checklistsFolder())
        // A folder that exists but can't be listed means no permission yet: try again next start.
        if (folder.exists() && folder.listFiles() == null) return
        val store = ChecklistStore(folder)
        val weeks = store.weeks().mapNotNull { store.load(it) }
        d.importWeeks(weeks)
        d.filesImported = true
        persist(context, d)
    }

    /** Written to a temp file and moved into place, so a crash never leaves half a store. */
    private fun persist(context: Context, d: ChecklistData) {
        val f = file(context)
        val tmp = File(f.parentFile, "checklists.json.tmp")
        tmp.writeText(d.toJson().toString())
        if (!tmp.renameTo(f)) {
            f.writeText(tmp.readText())
            tmp.delete()
        }
    }

    internal fun changedBySync() {
        _version.value++
    }
}

/**
 * Two-way sync of the checklists with the Nibras server: push the rows changed here, take back
 * everything changed there since the last round. Who wins when both sides changed the same row is
 * the server's call (its `checklists.sync.winner` setting); the phone just applies what comes back.
 *
 * Runs with the rest of the sync ([com.knotes.nibras.SyncCenter.syncNow]), when the checklists page
 * opens, and a few seconds after an edit — debounced, so a run of ticks goes as one request.
 */
object ChecklistSync {

    private val api = NibrasApi()
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null

    /** How long after the last edit the push goes out. */
    private const val DEBOUNCE_MS = 3_000L

    /** One round. False when the server isn't set up or couldn't be reached. */
    suspend fun sync(context: Context): Boolean = mutex.withLock {
        val app = context.applicationContext
        val settings = SettingsStore(app).value
        val base = settings.nibrasBaseUrl.trim()
        if (!settings.syncActive || base.isEmpty() || !settings.checklistsEnabled) return@withLock false
        NibrasApi.configure(base, settings.nibrasUsername, settings.nibrasPassword)
        val out = ChecklistRepo.read(app) { it.outgoing() }
        val answer = api.syncChecklists(base, out.body) ?: return@withLock false
        val json = runCatching { JSONObject(answer) }.getOrNull() ?: return@withLock false
        ChecklistRepo.write(app) { d ->
            d.applyIncoming(json, out.stamps)
            true
        }
        ChecklistRepo.changedBySync()
        // A group's reminder time is part of its name, which the web may just have changed.
        ChecklistReminders.sync(app)
        true
    }

    /** A sync [DEBOUNCE_MS] from now, replacing one already waiting. */
    fun schedule(context: Context) {
        val app = context.applicationContext
        val settings = SettingsStore(app).value
        if (!settings.syncActive || settings.nibrasBaseUrl.isBlank()) return
        synchronized(this) {
            pending?.cancel()
            pending = scope.launch {
                delay(DEBOUNCE_MS)
                runCatching { sync(app) }
            }
        }
    }

    /** Fire-and-forget round, for the page opening. */
    fun syncSoon(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { sync(app) } }
    }
}
