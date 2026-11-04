package com.knotes.collect

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class CollectStatus { Idle, Scanning, Ready, Collecting, Done }

/** Which collector the page is showing: the folders one (moves) or the WhatsApp one (copies). */
enum class CollectSource { FOLDERS, WHATSAPP }

class CollectViewModel(app: Application) : AndroidViewModel(app) {

    private val _status = MutableStateFlow(CollectStatus.Idle)
    val status: StateFlow<CollectStatus> = _status.asStateFlow()

    private val _source = MutableStateFlow(CollectSource.FOLDERS)
    val source: StateFlow<CollectSource> = _source.asStateFlow()

    private val _found = MutableStateFlow<List<FoundFile>>(emptyList())
    val found: StateFlow<List<FoundFile>> = _found.asStateFlow()

    private val _results = MutableStateFlow<List<CollectedFile>>(emptyList())
    val results: StateFlow<List<CollectedFile>> = _results.asStateFlow()

    private val _targetPath = MutableStateFlow<String?>(null)
    val targetPath: StateFlow<String?> = _targetPath.asStateFlow()

    /** WhatsApp scan: the folder read and the cut-off used (files modified after it are new). */
    private val _whatsAppDir = MutableStateFlow<File?>(null)
    val whatsAppDir: StateFlow<File?> = _whatsAppDir.asStateFlow()
    private val _whatsAppSince = MutableStateFlow(0L)
    val whatsAppSince: StateFlow<Long> = _whatsAppSince.asStateFlow()
    private var scanStartedAt = 0L

    /** Everything new the WhatsApp scan found, before the type filter; [found] is the ticked part. */
    private val _whatsAppAll = MutableStateFlow<List<FoundFile>>(emptyList())
    val whatsAppAll: StateFlow<List<FoundFile>> = _whatsAppAll.asStateFlow()
    private val _kinds = MutableStateFlow(WhatsAppCollect.selectedKinds(app))
    val kinds: StateFlow<Set<WhatsAppCollect.Kind>> = _kinds.asStateFlow()

    /** Tick or untick a WhatsApp file type; remembered for next time. */
    fun toggleKind(kind: WhatsAppCollect.Kind) {
        val next = if (kind in _kinds.value) _kinds.value - kind else _kinds.value + kind
        _kinds.value = next
        WhatsAppCollect.saveSelectedKinds(getApplication(), next)
        if (_source.value == CollectSource.WHATSAPP && _status.value == CollectStatus.Ready) {
            _found.value = _whatsAppAll.value.filter { WhatsAppCollect.kindOf(it) in next }
        }
    }

    fun scan(sourceDirs: List<String>, newDir: String, format: String) {
        _source.value = CollectSource.FOLDERS
        viewModelScope.launch {
            _status.value = CollectStatus.Scanning
            _results.value = emptyList()
            _targetPath.value = null
            val files = withContext(Dispatchers.IO) { FileCollector.scan(sourceDirs, newDir, format) }
            _found.value = files
            _status.value = CollectStatus.Ready
        }
    }

    /** How far back the WhatsApp scan reaches (since the last copy, unless chosen otherwise). */
    private val _range = MutableStateFlow(WhatsAppCollect.Range.SINCE_LAST)
    val range: StateFlow<WhatsAppCollect.Range> = _range.asStateFlow()

    fun changeRange(range: WhatsAppCollect.Range, format: String) {
        _range.value = range
        scanWhatsApp("", format)
    }

    /** Find what WhatsApp received since the last WhatsApp collection. */
    fun scanWhatsApp(configuredDir: String, format: String) {
        _source.value = CollectSource.WHATSAPP
        viewModelScope.launch {
            _status.value = CollectStatus.Scanning
            _results.value = emptyList()
            _targetPath.value = null
            scanStartedAt = System.currentTimeMillis()
            val ctx = getApplication<Application>()
            val dir = WhatsAppCollect.mediaDir(configuredDir)
            // Scan from the earliest kind's mark, then hold each file to its own kind's.
            val sinceBy = WhatsAppCollect.sinceByKind(ctx, scanStartedAt)
            val since = sinceBy.values.min()
            _whatsAppDir.value = dir
            val range = _range.value
            _whatsAppSince.value = WhatsAppCollect.cutoff(range, scanStartedAt)
                ?: (_kinds.value.minOfOrNull { sinceBy.getValue(it) } ?: since)
            val files = withContext(Dispatchers.IO) { WhatsAppCollect.scanNew(ctx, dir, format, scanStartedAt, range) }
            _whatsAppAll.value = files
            _found.value = files.filter { WhatsAppCollect.kindOf(it) in _kinds.value }
            _status.value = CollectStatus.Ready
        }
    }

    fun collect(
        newDir: String,
        format: String,
        type: String?,
        priority: Int,
        category: String?,
        description: String = "",
        heading: (Int) -> String,
    ) {
        val toCollect = _found.value
        if (toCollect.isEmpty()) return
        val whatsApp = _source.value == CollectSource.WHATSAPP
        val kinds = _kinds.value
        viewModelScope.launch {
            _status.value = CollectStatus.Collecting
            val (target, results) = withContext(Dispatchers.IO) {
                FileCollector.collect(
                    toCollect, newDir, format, type, priority, category,
                    description = if (whatsApp) WhatsAppCollect.NOTE_TITLE else description,
                    heading = heading,
                    copy = whatsApp,
                )
            }
            // Only a collection that copied everything moves the mark on: a failed file must
            // still count as new next time, or it would be skipped for good.
            // Only the TICKED kinds are marked: an unticked kind's new files stay new.
            if (whatsApp && results.all { it.ok }) {
                WhatsAppCollect.markCollected(getApplication(), kinds, scanStartedAt)
            }
            _results.value = results
            _targetPath.value = target.absolutePath
            _found.value = emptyList()
            _status.value = CollectStatus.Done
        }
    }
}
