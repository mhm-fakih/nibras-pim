package com.knotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import com.knotes.R
import com.knotes.quran.QuranAya
import com.knotes.quran.QuranLinkedNote
import com.knotes.quran.QuranStore
import com.knotes.quran.QuranSurah
import com.knotes.util.FriendlyTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GREEN = Color(0xFF2E7D32)
/** Notes longer than this start folded. */
private const val FOLD = 600

/**
 * The Quran module on the phone: the surahs (mushaf or revelation order, a filter, bookmarked
 * only), and a surah's ayat — vocalized, with the exegesis, the notes and the notes linked to each
 * aya. A surah and an aya can be bookmarked, and a note added to an aya, here and offline; the
 * server gets them as soon as it can be reached, a note appended after the aya's existing notes.
 */
@Composable
fun QuranScreen(baseUrl: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val overview by QuranStore.overview.collectAsState()
    val pending by QuranStore.pending.collectAsState()
    val tick by com.knotes.nibras.ServerRefresh.tick.collectAsState()
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    var reached by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(baseUrl, tick) {
        withContext(Dispatchers.IO) {
            QuranStore.seed(context)
            reached = QuranStore.refreshOverview(context, baseUrl)
            if (reached == true) QuranStore.drain(context, baseUrl)
        }
    }

    val current = open
    if (current != null) {
        BackHandler { open = null }
        SurahReader(baseUrl, current, onBack = { open = null })
        return
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        val ov = overview
        when {
            ov == null && reached == false -> Message(stringResource(R.string.quran_unreachable))
            ov == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> {
                if (!ov.enabled) Banner(stringResource(R.string.quran_off))
                if (ov.surahs.isEmpty()) {
                    Message(stringResource(R.string.quran_empty))
                } else {
                    SurahList(ov.surahs, pending.size, pending.count { it.error != null },
                        onOpen = { open = it },
                        onBookmark = { n, on -> QuranStore.bookmarkSurah(context, n, on); com.knotes.nibras.PushCenter.drainAll(context) },
                        onDiscardRefused = { pending.filter { it.error != null }.forEach { QuranStore.discard(context, it.ref) } },
                        onSaveAll = { progress ->
                            scope.launch(Dispatchers.IO) {
                                val all = ov.surahs.map { it.number }
                                all.forEachIndexed { i, n ->
                                    progress(i + 1, all.size)
                                    if (QuranStore.fetchSurah(context, baseUrl, n) == null) return@launch
                                }
                                progress(0, 0)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Banner(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun SurahList(
    surahs: List<com.knotes.quran.QuranSurahRow>,
    pendingCount: Int,
    refusedCount: Int,
    onOpen: (Int) -> Unit,
    onBookmark: (Int, Boolean) -> Unit,
    onDiscardRefused: () -> Unit,
    onSaveAll: ((Int, Int) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var filter by rememberSaveable { mutableStateOf("") }
    var revelation by rememberSaveable { mutableStateOf(false) }
    var onlyMarked by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var savedCount by remember { mutableStateOf(QuranStore.savedSurahs(context).size) }

    val shown = remember(surahs, filter, revelation, onlyMarked) {
        val f = filter.trim()
        surahs
            .let { if (revelation) it.sortedBy { s -> s.revelationOrder ?: 999 } else it }
            .filter { !onlyMarked || it.bookmarked || it.bookmarkedAyat > 0 }
            .filter { f.isEmpty() || it.name.contains(f) || it.number.toString() == f }
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.quran_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = {
            onSaveAll { done, total ->
                saving = if (total == 0) null else done to total
                if (total == 0 || done == total) savedCount = QuranStore.savedSurahs(context).size
            }
        }) { Icon(Icons.Filled.CloudDownload, contentDescription = stringResource(R.string.quran_save_all)) }
    }
    Text(
        saving?.let { stringResource(R.string.quran_saving, it.first, it.second) }
            ?: stringResource(R.string.quran_saved_count, savedCount, surahs.size),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 14.dp),
    )
    if (pendingCount > 0) Banner(stringResource(R.string.quran_pending, pendingCount))
    if (refusedCount > 0) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.quran_refused, refusedCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            TextButton(onClick = onDiscardRefused) { Text(stringResource(R.string.quran_discard)) }
        }
    }
    OutlinedTextField(
        value = filter,
        onValueChange = { filter = it },
        singleLine = true,
        placeholder = { Text(stringResource(R.string.quran_filter)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    )
    Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !revelation, onClick = { revelation = false }, label = { Text(stringResource(R.string.quran_order_mushaf)) })
        FilterChip(selected = revelation, onClick = { revelation = true }, label = { Text(stringResource(R.string.quran_order_revelation)) })
        FilterChip(selected = onlyMarked, onClick = { onlyMarked = !onlyMarked }, label = { Text(stringResource(R.string.quran_only_bookmarked)) })
    }
    HorizontalDivider(Modifier.padding(top = 4.dp))
    LazyColumn(Modifier.fillMaxSize()) {
        items(shown, key = { it.number }) { s ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(s.number) }.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    (if (revelation) s.revelationOrder ?: s.number else s.number).toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(34.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        s.name,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Serif,
                        textAlign = TextAlign.Right,
                        style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.quran_ayat, s.ayaCount) +
                            (if (s.bookmarkedAyat > 0) " · ★${s.bookmarkedAyat}" else "") +
                            (if (s.withNotes + s.linked > 0) " · ✎${s.withNotes + s.linked}" else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                IconButton(onClick = { onBookmark(s.number, !s.bookmarked) }) {
                    Icon(
                        if (s.bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = stringResource(if (s.bookmarked) R.string.quran_unbookmark_surah else R.string.quran_bookmark_surah),
                        tint = if (s.bookmarked) GREEN else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SurahReader(baseUrl: String, number: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val version by QuranStore.version.collectAsState()
    val pending by QuranStore.pending.collectAsState()
    var surah by remember(number) { mutableStateOf<QuranSurah?>(null) }
    var offlineSince by remember(number) { mutableStateOf<Long?>(null) }
    var missing by remember(number) { mutableStateOf(false) }
    var noteFor by remember { mutableStateOf<QuranAya?>(null) }

    // On opening: the saved copy at once, then the server's.
    LaunchedEffect(number) {
        withContext(Dispatchers.IO) {
            val cached = QuranStore.cachedSurah(context, number)
            if (cached != null) surah = cached
            val fresh = QuranStore.fetchSurah(context, baseUrl, number)
            when {
                fresh != null -> { surah = fresh; offlineSince = null }
                cached != null -> offlineSince = QuranStore.savedAt(context, number)
                else -> missing = true
            }
        }
    }
    // A change made here (queued) or one the server took (a sync): the saved copy again.
    LaunchedEffect(version, pending) {
        withContext(Dispatchers.IO) { QuranStore.cachedSurah(context, number)?.let { surah = it } }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        val s = surah
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.quran_back)) }
            Text(
                if (s != null) "سورة ${s.name}" else "…",
                fontSize = 24.sp,
                fontFamily = FontFamily.Serif,
                color = GREEN,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Rtl),
                modifier = Modifier.weight(1f),
            )
            if (s != null) {
                IconButton(onClick = {
                    QuranStore.bookmarkSurah(context, s.number, !s.bookmarked)
                    com.knotes.nibras.PushCenter.drainAll(context)
                }) {
                    Icon(
                        if (s.bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = stringResource(if (s.bookmarked) R.string.quran_unbookmark_surah else R.string.quran_bookmark_surah),
                        tint = if (s.bookmarked) GREEN else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        offlineSince?.let { Banner(stringResource(R.string.quran_offline, FriendlyTime.ago(context, it))) }
        HorizontalDivider()
        when {
            s != null -> {
                val list = rememberLazyListState()
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 10.dp), state = list) {
                    if (s.notes.isNotBlank()) item { Column { NotesBlock(stringResource(R.string.quran_notes), s.notes, emptyList()) } }
                    items(s.ayat, key = { it.number }) { a ->
                        AyaCard(
                            surah = s.number,
                            aya = a,
                            onBookmark = {
                                QuranStore.bookmarkAya(context, s.number, a.number, !a.bookmarked)
                                com.knotes.nibras.PushCenter.drainAll(context)
                            },
                            onNote = { noteFor = a },
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
            missing -> Message(stringResource(R.string.quran_not_saved))
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }

    noteFor?.let { a ->
        var text by remember(a.number) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { noteFor = null },
            title = { Text("${stringResource(R.string.quran_add_note)} · $number:${a.number}") },
            text = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text(stringResource(R.string.quran_note_hint)) },
                        minLines = 4,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl, textAlign = TextAlign.Right),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    QuranStore.addNote(context, number, a.number, text)
                    com.knotes.nibras.PushCenter.drainAll(context)
                    noteFor = null
                }) { Text(stringResource(R.string.quran_note_save)) }
            },
            dismissButton = { TextButton(onClick = { noteFor = null }) { Text(stringResource(R.string.quran_cancel)) } },
        )
    }
}

@Composable
private fun AyaCard(surah: Int, aya: QuranAya, onBookmark: () -> Unit, onNote: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$surah:${aya.number}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            IconButton(onClick = onNote) { Icon(Icons.Filled.EditNote, contentDescription = stringResource(R.string.quran_add_note)) }
            IconButton(onClick = onBookmark) {
                Icon(
                    if (aya.bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = stringResource(if (aya.bookmarked) R.string.quran_unbookmark_aya else R.string.quran_bookmark_aya),
                    tint = if (aya.bookmarked) GREEN else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            (aya.vocalized.ifBlank { aya.text }).trim() + "  ﴿${aya.number}﴾",
            fontSize = 23.sp,
            lineHeight = 44.sp,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Right,
            style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl),
            modifier = Modifier.fillMaxWidth(),
        )
        if (aya.exegesis.isNotBlank()) {
            Section(stringResource(R.string.quran_exegesis), GREEN)
            RtlText(aya.exegesis.trim(), 16.sp)
        }
        if (aya.notes.isNotBlank() || aya.pendingNotes.isNotEmpty()) {
            NotesBlock(stringResource(R.string.quran_notes), aya.notes, aya.pendingNotes)
        }
        if (aya.linked.isNotEmpty()) {
            Section(stringResource(R.string.quran_linked, countNotes(aya.linked)), MaterialTheme.colorScheme.onSurfaceVariant)
            LinkedNotes(aya.linked, 0)
        }
    }
}

private fun countNotes(ns: List<QuranLinkedNote>): Int = ns.sumOf { 1 + countNotes(it.children) }

@Composable
private fun Section(label: String, color: Color) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = color,
        textAlign = TextAlign.Right,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun RtlText(text: String, size: androidx.compose.ui.unit.TextUnit, color: Color = Color.Unspecified) {
    Text(
        text,
        fontSize = size,
        lineHeight = size * 1.7f,
        color = color,
        textAlign = TextAlign.Right,
        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Rtl),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.NotesBlock(label: String, notes: String, waiting: List<String>) {
    var unfold by remember(notes) { mutableStateOf(false) }
    val body = notes.trim()
    val long = body.length > FOLD
    Section(label, MaterialTheme.colorScheme.onSurfaceVariant)
    if (body.isNotEmpty()) {
        RtlText(if (long && !unfold) body.take(FOLD) + "…" else body, 15.sp)
        if (long) {
            TextButton(onClick = { unfold = !unfold }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(if (unfold) R.string.quran_show_less else R.string.quran_show_all))
            }
        }
    }
    waiting.forEach { w ->
        Text(
            stringResource(R.string.quran_waiting),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
            textAlign = TextAlign.Right,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        RtlText(w, 15.sp, MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun LinkedNotes(notes: List<QuranLinkedNote>, depth: Int) {
    notes.forEach { n ->
        Column(Modifier.fillMaxWidth().padding(start = (depth * 14).dp, top = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        (if (depth > 0) "↳ " else "") + n.title.ifBlank { "#${n.id}" },
                        fontWeight = FontWeight.SemiBold,
                        color = GREEN,
                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val t = n.text.trim()
                    if (t.isNotEmpty() && t != n.title.trim()) {
                        Text(
                            if (t.length > 900) t.take(900) + "…" else t,
                            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Box(Modifier.width(2.dp).height(18.dp).background(Color(0xFFC8E6C9)))
            }
            if (n.children.isNotEmpty()) LinkedNotes(n.children, depth + 1)
        }
    }
}
