package com.knotes.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.knotes.R
import com.knotes.excerpts.ExcerptLibrary
import com.knotes.excerpts.LibraryOverview
import com.knotes.excerpts.SampleDetail
import com.knotes.excerpts.SampleRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val GREEN = Color(0xFF2E7D32)
private val STATUSES = listOf("new", "summarized", "promoted", "skipped", "all")

/** Arabic, Persian, Hebrew… — right to left, right-aligned (the rule everywhere in the app). */
private fun rtl(s: String?): Boolean =
    s?.firstOrNull { Character.isLetter(it) }?.let {
        val d = Character.getDirectionality(it)
        d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
    } ?: false

private fun align(s: String?) = if (rtl(s)) TextAlign.Right else TextAlign.Left

/**
 * The web's Excerpt library on the phone: 2–3 pages taken at random from the ebk books, to read
 * and give a one-line summary and details, then promote to an Excerpt of the book or set aside.
 * The samples are made on the server ("Make more": random books, cut with the PDF/DjVu/Calibre
 * tools — no AI), so this page needs the server.
 */
@Composable
fun ExcerptLibraryScreen(baseUrl: String, filesDir: String) {
    var status by rememberSaveable { mutableStateOf("new") }
    var query by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    var rows by remember { mutableStateOf<List<SampleRow>?>(null) }
    var overview by remember { mutableStateOf<LibraryOverview?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val tick by com.knotes.nibras.ServerRefresh.tick.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(baseUrl, status, query, reload, tick) {
        if (query.isNotEmpty()) delay(350)
        runCatching {
            overview = ExcerptLibrary.overview(baseUrl)
            rows = ExcerptLibrary.list(baseUrl, status, query)
        }.onSuccess { error = null }.onFailure { error = it.message ?: it.toString() }
    }
    // While a batch is being made, its progress and the list follow it.
    val running = overview?.job?.running == true
    LaunchedEffect(running) {
        while (running) {
            delay(3000)
            runCatching { overview = ExcerptLibrary.overview(baseUrl) }
            if (overview?.job?.running != true) reload++
        }
    }

    val current = open
    if (current != null) {
        BackHandler { open = null; reload++ }
        SampleScreen(
            baseUrl = baseUrl,
            filesDir = filesDir,
            id = current,
            onBack = { open = null; reload++ },
            onOpen = { open = it },
            nextOf = { id -> rows?.let { rs -> rs.getOrNull(rs.indexOfFirst { it.id == id } + 1)?.id } },
        )
        return
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.excerpts_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            overview?.counts?.let { c ->
                Text(
                    stringResource(R.string.excerpts_counts, c.total, c.sampledBooks - c.failed, c.books, c.fresh, c.promoted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                var count by rememberSaveable { mutableIntStateOf(20) }
                listOf(10, 20, 50).forEach { n ->
                    FilterChip(selected = count == n, onClick = { count = n }, label = { Text("$n") },
                        modifier = Modifier.padding(end = 6.dp))
                }
                Button(
                    enabled = !running,
                    onClick = {
                        scope.launch {
                            runCatching { ExcerptLibrary.generate(baseUrl, count); overview = ExcerptLibrary.overview(baseUrl) }
                                .onFailure { error = it.message }
                        }
                    },
                ) {
                    Icon(Icons.Filled.Casino, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (running) R.string.excerpts_making else R.string.excerpts_make_more))
                }
            }
            overview?.job?.takeIf { it.running || it.finished }?.let { j ->
                LinearProgressIndicator(
                    progress = { if (j.wanted > 0) j.made / j.wanted.toFloat() else 0f },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                Text(
                    if (j.running) stringResource(R.string.excerpts_job_running, j.made, j.wanted, j.tried)
                    else stringResource(R.string.excerpts_job_done, j.made, j.wanted, j.failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp)) {
                val labels = listOf(R.string.excerpts_status_new, R.string.excerpts_status_summarized,
                    R.string.excerpts_status_promoted, R.string.excerpts_status_skipped, R.string.excerpts_status_all)
                STATUSES.forEachIndexed { i, s ->
                    FilterChip(selected = status == s, onClick = { status = s; rows = null },
                        label = { Text(stringResource(labels[i])) }, modifier = Modifier.padding(end = 6.dp))
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text(stringResource(R.string.excerpts_search)) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        error?.let {
            Text(stringResource(R.string.excerpts_error, it), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp))
            TextButton(onClick = { reload++ }, modifier = Modifier.padding(horizontal = 4.dp)) { Text(stringResource(R.string.excerpts_retry)) }
        }
        val rs = rows
        when {
            rs == null -> if (error == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            rs.isEmpty() -> Text(
                stringResource(if (status == "new") R.string.excerpts_none_new else R.string.excerpts_none),
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp),
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(rs, key = { it.id }) { r ->
                    RowItem(r, baseUrl) { open = r.id }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun RowItem(r: SampleRow, baseUrl: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)) {
    BookCover(baseUrl, r.coverLink, 40.dp)
    if (r.coverLink != null) Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
        val title = r.book ?: "#${r.bookId}"
        Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = align(title), style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
            modifier = Modifier.fillMaxWidth())
        val line = r.summary ?: r.preview?.replace(Regex("\\s+"), " ")
        if (!line.isNullOrBlank()) {
            Text(line, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = align(line),
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                color = if (r.summary != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth())
        }
        Text(
            listOfNotNull(r.format.uppercase(), ExcerptLibrary.place(r).ifBlank { null },
                if (r.hasText) null else stringResource(R.string.excerpts_image_only)).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    }
}

/** The book's cover (a signed link: no sign-in needed), 2:3; nothing when the book has none. */
@Composable
private fun BookCover(baseUrl: String, link: String?, width: androidx.compose.ui.unit.Dp) {
    if (link == null) return
    AsyncImage(
        model = ExcerptLibrary.absolute(baseUrl, link),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.width(width).aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SampleScreen(
    baseUrl: String,
    filesDir: String,
    id: Long,
    onBack: () -> Unit,
    onOpen: (Long?) -> Unit,
    nextOf: (Long) -> Long?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var d by remember(id) { mutableStateOf<SampleDetail?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var summary by rememberSaveable(id) { mutableStateOf("") }
    var details by rememberSaveable(id) { mutableStateOf("") }
    var view by rememberSaveable(id) { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var loaded by remember(id) { mutableIntStateOf(0) }

    LaunchedEffect(id, loaded) {
        runCatching { ExcerptLibrary.get(baseUrl, id) }
            .onSuccess {
                d = it; error = null
                summary = it.row.summary.orEmpty(); details = it.description.orEmpty()
                view = if (it.text != null) 0 else 1
            }
            .onFailure { error = it.message ?: it.toString() }
    }

    fun run(block: suspend () -> SampleDetail, after: (SampleDetail) -> Unit = {}) {
        busy = true
        scope.launch {
            runCatching { block() }
                .onSuccess { fresh ->
                    d = fresh; error = null
                    summary = fresh.row.summary.orEmpty(); details = fresh.description.orEmpty()
                    after(fresh)
                }
                .onFailure { error = it.message ?: it.toString() }
            busy = false
        }
    }

    // The sample's PDF, fetched once to the phone, then shown full screen in the app's PDF viewer
    // (pen and highlighter, saved into the file) or handed to another app.
    var fetching by remember { mutableStateOf(false) }
    fun withPdf(use: (java.io.File) -> Unit) {
        val det = d ?: return
        fetching = true
        scope.launch {
            runCatching { ExcerptLibrary.localPdf(baseUrl, filesDir, det) }
                .onSuccess { error = null; use(it) }
                .onFailure { error = it.message ?: it.toString() }
            fetching = false
        }
    }
    val readAt = { page: Int -> withPdf { InAppViewer.startPage = page; InAppViewer.file = it } }
    val readInApp = { readAt(0) }
    val openElsewhere = { withPdf { openLocalFile(context, it) } }

    val dd = d
    val dirty = dd != null && (summary != dd.row.summary.orEmpty() || details != dd.description.orEmpty())

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.excerpts_back)) }
            Text(dd?.row?.book ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = align(dd?.row?.book),
                modifier = Modifier.weight(1f).padding(end = 12.dp))
        }
        if (dd == null) {
            if (error != null) {
                Text(stringResource(R.string.excerpts_error, error!!), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                TextButton(onClick = { loaded++ }) { Text(stringResource(R.string.excerpts_retry)) }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            return@Column
        }
        val r = dd.row
        val pageUrls = dd.pageLink?.let { link ->
            (1..dd.pages).map { n -> ExcerptLibrary.absolute(baseUrl, "$link&n=$n&w=1280") }
        }.orEmpty()

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            item {
                Row {
                BookCover(baseUrl, r.coverLink, 72.dp)
                if (r.coverLink != null) Spacer(Modifier.width(12.dp))
                Text(
                    listOfNotNull(r.author, r.format.uppercase(), ExcerptLibrary.place(r).ifBlank { null }, statusLabel(r.status))
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                }
                if (dd.pdfLink != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp)) {
                        Button(enabled = !fetching, onClick = readInApp) {
                            Icon(Icons.Filled.Fullscreen, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.excerpts_read_full))
                        }
                        OutlinedButton(enabled = !fetching, onClick = openElsewhere) {
                            Icon(Icons.Filled.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.excerpts_open_with))
                        }
                        if (fetching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    dd.sourceLink?.let { link ->
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ExcerptLibrary.absolute(baseUrl, link)))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }) {
                            Icon(Icons.Filled.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.excerpts_open_book))
                        }
                    }
                    if (r.status != "promoted") {
                        TextButton(enabled = !busy, onClick = {
                            run({ ExcerptLibrary.act(baseUrl, id, "another") }) { if (it.row.id != id) onOpen(it.row.id) }
                        }) {
                            Icon(Icons.Filled.Casino, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.excerpts_another))
                        }
                    }
                    if (r.status == "skipped") {
                        TextButton(enabled = !busy, onClick = { run({ ExcerptLibrary.act(baseUrl, id, "restore") }) }) {
                            Icon(Icons.Filled.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.excerpts_restore))
                        }
                    } else if (r.status != "promoted") {
                        TextButton(enabled = !busy, onClick = { run({ ExcerptLibrary.act(baseUrl, id, "skip") }) }) {
                            Icon(Icons.Filled.DoNotDisturbOn, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.excerpts_skip))
                        }
                    }
                    if (r.excerptId == null) {
                        OutlinedButton(enabled = !busy && !r.summary.isNullOrBlank() && !dirty,
                            onClick = { run({ ExcerptLibrary.act(baseUrl, id, "promote") }) }) {
                            Icon(Icons.Filled.Upgrade, null, Modifier.size(18.dp), tint = GREEN); Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.excerpts_promote), color = GREEN)
                        }
                    } else {
                        Text(stringResource(R.string.excerpts_promoted_as, r.excerptId), color = GREEN,
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.CenterVertically))
                    }
                }
                if (r.excerptId == null && r.summary.isNullOrBlank()) {
                    Text(stringResource(R.string.excerpts_promote_hint), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error?.let { Text(stringResource(R.string.excerpts_error, it), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall) }

                // The summary first: it is what this page is for.
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                ) {
                    OutlinedTextField(
                        value = summary,
                        onValueChange = { summary = it.replace('\n', ' ').take(255) },
                        label = { Text(stringResource(R.string.excerpts_summary)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content, textAlign = align(summary)),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (dirty) run({ ExcerptLibrary.save(baseUrl, id, summary, details) }) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = details,
                        onValueChange = { details = it },
                        label = { Text(stringResource(R.string.excerpts_details)) },
                        minLines = 2, maxLines = 8,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content,
                            textAlign = align(details.ifBlank { summary })),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy && dirty, onClick = { run({ ExcerptLibrary.save(baseUrl, id, summary, details) }) }) {
                            Text(stringResource(R.string.excerpts_save))
                        }
                        OutlinedButton(enabled = !busy, onClick = {
                            val next = nextOf(id)
                            if (dirty) run({ ExcerptLibrary.save(baseUrl, id, summary, details) }) { onOpen(next) } else onOpen(next)
                        }) {
                            Text(stringResource(if (dirty) R.string.excerpts_save_next else R.string.excerpts_next))
                        }
                    }
                }

                if (dd.text != null || pageUrls.isNotEmpty()) {
                    TabRow(selectedTabIndex = view, modifier = Modifier.padding(bottom = 8.dp)) {
                        Tab(selected = view == 0, enabled = dd.text != null, onClick = { view = 0 },
                            text = { Text(stringResource(R.string.excerpts_text)) })
                        Tab(selected = view == 1, enabled = pageUrls.isNotEmpty(), onClick = { view = 1 },
                            text = { Text(stringResource(R.string.excerpts_pages, pageUrls.size)) })
                    }
                } else {
                    Text(stringResource(R.string.excerpts_nothing) + (dd.error?.let { " $it" } ?: ""),
                        color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            if (view == 0 && dd.text != null) {
                val paras = dd.text.split(Regex("\\n\\s*\\n")).map { ExcerptLibrary.reflow(it.trim()) }.filter { it.isNotEmpty() }
                items(paras) { p ->
                    Text(p, fontSize = 17.sp, lineHeight = 30.sp, textAlign = align(p),
                        style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
                }
            }
            if (view == 1) {
                if (dd.pdfLink != null) item {
                    Text(stringResource(R.string.excerpts_pages_tap), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                }
                itemsIndexed(pageUrls) { i, u ->
                    AsyncImage(
                        model = u,
                        contentDescription = "p. ${(r.pageFrom ?: 1) + i}",
                        contentScale = ContentScale.FillWidth,
                        // A tap opens the pages full screen in the PDF viewer (zoom, pen, highlighter).
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).background(Color.White)
                            .clickable(enabled = dd.pdfLink != null && !fetching) { readAt(i) },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun statusLabel(s: String): String = stringResource(
    when (s) {
        "summarized" -> R.string.excerpts_status_summarized
        "promoted" -> R.string.excerpts_status_promoted
        "skipped" -> R.string.excerpts_status_skipped
        else -> R.string.excerpts_status_new
    },
)
