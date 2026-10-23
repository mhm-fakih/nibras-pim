package com.knotes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val settingsStore = SettingsStore(app)
    private val repo = NoteRepository(settingsStore)

    /**
     * The AI-answers folder. A second repository rather than a second mode on the first: this one
     * is read-only and may point at a folder that does not exist yet, and giving [repo] an optional
     * directory would put that "might not be there" case on the path the editor uses.
     */
    private val aiRepo = NoteRepository(settingsStore) { settingsStore.value.aiDir() }

    private val _aiNotes = MutableStateFlow<List<Note>>(emptyList())
    val aiNotes: StateFlow<List<Note>> = _aiNotes.asStateFlow()

    /** Where the AI answers are read from, and whether that folder is actually there. */
    fun aiDirPath(): String = aiRepo.dirPath()
    fun aiDirExists(): Boolean = aiRepo.dirExists()

    /**
     * Re-read the AI folder. Called when the page opens and when its refresh is tapped, not on a
     * timer: the folder changes only when Syncthing writes to it, and polling a few hundred files
     * in the background is work nobody asked for.
     */
    fun refreshAiNotes() {
        viewModelScope.launch(Dispatchers.IO) {
            _aiNotes.value = if (aiRepo.dirExists()) aiRepo.listNotes() else emptyList()
        }
    }
    private val nibras = com.knotes.nibras.NibrasApi()

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    /**
     * Files in the notes folder that aren't canonical notes — Syncthing drops in whatever other
     * machines put there. Kept in its own flow rather than folded into [notes] so the counted set
     * stays exactly "real notes": the header, the status notification and the KPI widget all read
     * [notes], and mixing foreign files in would inflate every one of those numbers.
     * See [NoteRepository.listUnrecognized].
     */
    private val _unrecognized = MutableStateFlow<List<Note>>(emptyList())
    val unrecognized: StateFlow<List<Note>> = _unrecognized.asStateFlow()

    init {
        // Reload notes whenever settings change (e.g. the directory was edited).
        viewModelScope.launch {
            settingsStore.state.collect { refresh() }
        }
        syncTaxonomy()
    }

    /** Best-effort: refresh the "type" codes from the backend's modules and the "category" codes from
     *  its bookmarked departments, so captures map to the right record on import. Runs off the UI
     *  thread; on ANY failure (no URL, offline, bad credentials) it silently keeps the current cached/
     *  default codes — note-taking never depends on the network. */
    fun syncTaxonomy() {
        val base = settingsStore.value.nibrasBaseUrl.trim()
        if (base.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val s = settingsStore.value
            com.knotes.nibras.NibrasApi.configure(base, s.nibrasUsername, s.nibrasPassword)
            val types = runCatching { nibras.fetchModules(base) }.getOrNull()?.takeIf { it.isNotEmpty() }
            val cats = runCatching { nibras.fetchDepartments(base) }.getOrNull()?.takeIf { it.isNotEmpty() }
            if (types != null || cats != null) {
                val cur = settingsStore.value
                settingsStore.save(cur.copy(types = types ?: cur.types, categories = cats ?: cur.categories))
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _notes.value = repo.listNotes()
            _unrecognized.value = repo.listUnrecognized()
        }
    }

    fun add(text: String, type: String?, priority: Int, category: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.saveNew(text, type, priority, category)
            _notes.value = repo.listNotes()
            _unrecognized.value = repo.listUnrecognized()
            com.knotes.status.StatusService.refresh(getApplication())  // update today's count
            com.knotes.reminder.KpiWidget.refresh(getApplication())
            // A task typed in the app belongs on the Today's tasks widget as much as one typed
            // into the widget's own add bar; without this it would not appear there until the
            // next half-hourly redraw.
            com.knotes.reminder.TodayTasksWidget.refresh(getApplication())
            com.knotes.reminder.TasksBoardWidget.refresh(getApplication())
        }
    }

    fun update(note: Note, text: String, type: String?, priority: Int, category: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.update(note, text, type, priority, category)
            _notes.value = repo.listNotes()
            _unrecognized.value = repo.listUnrecognized()
        }
    }

    /**
     * Rewrite a note's body only, keeping its type/priority/category — so the file keeps its name.
     *
     * Used by the reading screen, where every tap saves. The general [update] takes the tag fields
     * because the editor can change them; passing the note's own values back through it is how a
     * body-only save avoids renaming the file (the name encodes the tags) and so avoids dragging
     * the note to a new position in the list on every highlight.
     */
    fun saveBody(note: Note, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // The note's file is gone: it was renamed under us, which is what a tag change does
            // (the name encodes the tags). Writing anyway would recreate the pre-rename file and
            // leave two notes. The rename already carried this body with it, so there is nothing
            // to save — and guessing is worse than doing nothing.
            if (!note.file.exists()) {
                return@launch
            }
            repo.update(note, text, note.type, note.priority ?: 0, note.category)
            _notes.value = repo.listNotes()
            _unrecognized.value = repo.listUnrecognized()
        }
    }

    /**
     * Change a note's tags (and body) and hand back the note as it now exists.
     *
     * The callback matters: the file NAME encodes the tags, so changing a priority renames the
     * file. A screen still holding the old [Note] would then write the body to a path that no
     * longer exists — recreating the pre-rename file and leaving two notes where there was one.
     * Callers must adopt the note this returns.
     */
    fun retag(
        note: Note,
        text: String,
        type: String?,
        priority: Int,
        category: String?,
        onDone: (Note?) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val target = repo.update(note, text, type, priority, category)
            val fresh = repo.listNotes()
            _notes.value = fresh
            _unrecognized.value = repo.listUnrecognized()
            val updated = fresh.firstOrNull { it.file.absolutePath == target.absolutePath }
            withContext(Dispatchers.Main) { onDone(updated) }
        }
    }

    fun delete(note: Note) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.delete(note)
            _notes.value = repo.listNotes()
            _unrecognized.value = repo.listUnrecognized()
            com.knotes.status.StatusService.refresh(getApplication())
            com.knotes.reminder.KpiWidget.refresh(getApplication())
        }
    }
}
