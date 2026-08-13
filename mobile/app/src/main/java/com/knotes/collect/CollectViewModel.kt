package com.knotes.collect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CollectStatus { Idle, Scanning, Ready, Collecting, Done }

class CollectViewModel : ViewModel() {

    private val _status = MutableStateFlow(CollectStatus.Idle)
    val status: StateFlow<CollectStatus> = _status.asStateFlow()

    private val _found = MutableStateFlow<List<FoundFile>>(emptyList())
    val found: StateFlow<List<FoundFile>> = _found.asStateFlow()

    private val _results = MutableStateFlow<List<CollectedFile>>(emptyList())
    val results: StateFlow<List<CollectedFile>> = _results.asStateFlow()

    private val _targetPath = MutableStateFlow<String?>(null)
    val targetPath: StateFlow<String?> = _targetPath.asStateFlow()

    fun scan(sourceDirs: List<String>, newDir: String, format: String) {
        viewModelScope.launch {
            _status.value = CollectStatus.Scanning
            _results.value = emptyList()
            _targetPath.value = null
            val files = withContext(Dispatchers.IO) { FileCollector.scan(sourceDirs, newDir, format) }
            _found.value = files
            _status.value = CollectStatus.Ready
        }
    }

    fun collect(newDir: String, format: String, type: String?, priority: Int, category: String?) {
        val toMove = _found.value
        if (toMove.isEmpty()) return
        viewModelScope.launch {
            _status.value = CollectStatus.Collecting
            val (target, results) = withContext(Dispatchers.IO) {
                FileCollector.collect(toMove, newDir, format, type, priority, category)
            }
            _results.value = results
            _targetPath.value = target.absolutePath
            _found.value = emptyList()
            _status.value = CollectStatus.Done
        }
    }
}
