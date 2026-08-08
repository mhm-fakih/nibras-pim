package com.knotes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val settingsStore = SettingsStore(app)
    private val repo = NoteRepository(settingsStore)
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
            val cats = runCatching { nibras.fetchBookmarkedDepartments(base) }.getOrNull()?.takeIf { it.isNotEmpty() }
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
        }
    }

    fun update(note: Note, text: String, type: String?, priority: Int, category: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.update(note, text, type, priority, category)
            _notes.value = repo.listNotes()
            _unrecognized.value = repo.listUnrecognized()
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
