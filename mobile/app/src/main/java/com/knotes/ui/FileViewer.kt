package com.knotes.ui

import android.content.Context
import android.annotation.SuppressLint
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.pdf.models.selection.SelectionBoundary
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.knotes.pdf.PdfAnnotator
import com.knotes.pdf.PdfMark
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material3.Surface
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knotes.R
import com.knotes.util.FileNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")

/**
 * Files the app shows itself, as it plays audio and video itself: images (in the annotator), PDFs,
 * and text/markdown (in the note reader, where a tap highlights).
 */
fun isViewableInApp(name: String): Boolean =
    FileNames.ext(name).let { it in IMAGE_EXT || it == "pdf" || it in com.knotes.util.Review.TEXT }

/**
 * The one file being viewed. Openers are plain functions (a card's attachment row, a server
 * record's file list), so they set this and the [FileViewerHost] at the app root shows it.
 */
object InAppViewer {
    var file by mutableStateOf<File?>(null)

    /** Bumped when the viewer renamed or deleted a file, so lists showing that folder re-read it. */
    var changed by mutableIntStateOf(0)

    /**
     * Bumped when a file gained a review trace (time marks saved, an image annotated, a text
     * highlighted), so the cards recolour its row without reloading the notes.
     */
    var reviewed by mutableIntStateOf(0)
}

/**
 * Open [file] the way the app opens anything: media in its player, images and PDFs in its viewer,
 * the rest in another app through the chooser.
 */
fun openFileInApp(context: Context, file: File, onPlayMedia: (File) -> Unit) {
    when {
        com.knotes.player.isMediaFile(file.name) -> onPlayMedia(file)
        file.isFile && isViewableInApp(file.name) -> InAppViewer.file = file
        else -> openLocalFile(context, file)
    }
}

/**
 * Placed once at the app root: the open file, full screen. An image opens in the gallery's
 * annotator (pen, highlighter, crop — the same tools as the Gallery page), paging through the
 * other images in its folder; a PDF opens in [FileViewer].
 */
@Composable
fun FileViewerHost() {
    val file = InAppViewer.file ?: return
    val close = { InAppViewer.file = null }
    val ext = FileNames.ext(file.name)
    val isPdf = ext == "pdf"
    val isText = ext in com.knotes.util.Review.TEXT
    // Drawn over the app inside its own window, not in a Dialog: a dialog's window ran under the
    // navigation bar and reported no insets, so the bottom controls sat behind it. The activity's
    // window already stops above the bars. The Surface takes every touch, so nothing reaches the
    // screen beneath; each viewer handles Back itself (the PDF and image ones save first).
    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        if (isText) {
            BackHandler(onBack = close)
            TextFileReader(file = file, onClose = close)
        } else if (isPdf) FileViewer(file = file, onClose = close)
        else GalleryScreen(dir = file.parentFile?.absolutePath ?: "/", startFile = file, onClose = close)
    }
}

/**
 * A text or markdown attachment in the note reader: read mode, tap a sentence to highlight it,
 * Edit to change it. Saved straight back to the file; no tag bar (it isn't a note, its name
 * carries no tags).
 */
@Composable
private fun TextFileReader(file: File, onClose: () -> Unit) {
    val text = remember(file) { runCatching { file.readText() }.getOrDefault("") }
    val note = remember(file, text) {
        com.knotes.Note(
            file = file,
            timestamp = "",
            type = null,
            priority = null,
            category = null,
            content = text,
            lastModified = file.lastModified(),
            createdMillis = file.lastModified(),
            isRtl = com.knotes.util.Bidi.isRtlLine(text) ?: false,
        )
    }
    val context = LocalContext.current
    val settings = remember { com.knotes.SettingsStore(context).value }
    NoteReadScreen(
        note = note,
        settings = settings,
        onSave = { body ->
            runCatching { file.writeText(body) }
            InAppViewer.reviewed++
        },
        onTags = { _, _, _, _ -> },
        onBack = onClose,
        showTags = false,
    )
}

/** The PDF viewer's tools. [NONE] reads and scrolls; the others take the drag (as in the gallery). */
private enum class PdfTool { NONE, PEN, HIGHLIGHT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileViewer(file: File, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var subtitle by remember(file) { mutableStateOf<String?>(null) }
    var tool by remember(file) { mutableStateOf(PdfTool.NONE) }
    val marks = remember(file) { mutableStateListOf<PdfMark>() }
    var saving by remember(file) { mutableStateOf(false) }
    var error by remember(file) { mutableStateOf<String?>(null) }

    // The PDF as last saved: bumped after each save, so the pages are re-read from the file and
    // show the marks as written (the pending overlay is then cleared).
    var version by remember(file) { mutableIntStateOf(0) }

    // Autosave: a moment after the last stroke the marks in hand are written into the file. Marks
    // made while a save runs stay pending and go out with the next one. A failed save keeps them
    // and says so.
    suspend fun saveNow(): Boolean {
        if (marks.isEmpty()) return true
        val batch = marks.toList()
        saving = true
        val result = withContext(Dispatchers.IO) { PdfAnnotator.save(context, file, batch) }
        saving = false
        return result.fold(
            onSuccess = {
                marks.removeAll(batch)
                error = null
                version++
                InAppViewer.reviewed++
                true
            },
            onFailure = { e ->
                error = context.getString(R.string.viewer_pdf_save_failed, e.message.orEmpty())
                false
            },
        )
    }
    LaunchedEffect(file) {
        snapshotFlow { marks.size }.collectLatest { n ->
            if (n > 0) {
                delay(1500)
                saveNow()
            }
        }
    }
    // Closing saves anything still pending first.
    val close: () -> Unit = {
        if (!saving) scope.launch { if (saveNow()) onClose() }
    }
    BackHandler(onBack = close)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = close) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.viewer_close))
                    }
                },
                title = {
                    Column {
                        Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                    }
                },
                actions = {
                    IconButton(onClick = { shareLocalFile(context, file) }) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.viewer_share))
                    }
                    IconButton(onClick = { openLocalFile(context, file) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.viewer_open_with))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                PdfTool.entries.forEach { t ->
                    FilterChip(
                        selected = tool == t,
                        onClick = { tool = t },
                        label = {
                            Text(
                                stringResource(
                                    when (t) {
                                        PdfTool.NONE -> R.string.widgets_gallery_tool_view
                                        PdfTool.PEN -> R.string.widgets_gallery_tool_pen
                                        PdfTool.HIGHLIGHT -> R.string.widgets_gallery_tool_highlight
                                    },
                                ),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                when (t) {
                                    PdfTool.NONE -> Icons.Filled.PanTool
                                    PdfTool.PEN -> Icons.Filled.Brush
                                    PdfTool.HIGHLIGHT -> Icons.Filled.FormatColorFill
                                },
                                null,
                                modifier = Modifier.size(15.dp),
                            )
                        },
                    )
                }
                if (marks.isNotEmpty()) {
                    IconButton(onClick = { marks.removeAt(marks.lastIndex) }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.widgets_gallery_undo_mark))
                    }
                }
            }
            Box(Modifier.fillMaxSize()) {
                PdfPages(
                    file,
                    version = version,
                    tool = tool,
                    marks = marks,
                    onPageCount = { n -> subtitle = context.resources.getQuantityString(R.plurals.viewer_pages, n, n) },
                    onOpenWith = { openLocalFile(context, file) },
                )
                if (saving) {
                    Text(
                        stringResource(R.string.viewer_saving),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .background(Color(0x99000000), MaterialTheme.shapes.small)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(12.dp)
                            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
                            .clickable { error = null }
                            .padding(12.dp),
                    )
                }
            }
        }
    }
}

/**
 * A PDF's pages one under the other, drawn with the platform's [PdfRenderer]. Pages are rendered
 * as they scroll into view. Pinch widens the pages — they are laid out larger, not just
 * magnified — so the list still scrolls normally and a zoomed page moves sideways with one finger.
 * With a tool armed the drag marks the page instead, and scrolling waits for View.
 */
@Composable
private fun PdfPages(
    file: File,
    version: Int,
    tool: PdfTool,
    marks: MutableList<PdfMark>,
    onPageCount: (Int) -> Unit,
    onOpenWith: () -> Unit,
) {
    val doc = remember(file, version) {
        runCatching {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfDoc(fd, PdfRenderer(fd))
        }
    }
    DisposableEffect(doc) { onDispose { doc.getOrNull()?.close() } }
    val pdf = doc.getOrNull()
    if (pdf == null) {
        // Encrypted or damaged: say so, and offer another app.
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.viewer_pdf_failed, doc.exceptionOrNull()?.message.orEmpty()))
            TextButton(onClick = onOpenWith) { Text(stringResource(R.string.viewer_open_with)) }
        }
        return
    }
    LaunchedEffect(pdf) { onPageCount(pdf.count) }

    var zoom by remember(file) { mutableFloatStateOf(1f) }
    val list = rememberLazyListState()
    val density = LocalDensity.current
    val reading = tool == PdfTool.NONE
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // Initial pass: two fingers zoom before the list sees them; one finger is left alone.
            .pointerInput(file) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            zoom = (zoom * event.calculateZoom()).coerceIn(1f, 4f)
                            event.calculatePan()
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val pageWidthPx = constraints.maxWidth
        val pageWidth = with(density) { (pageWidthPx * zoom).toDp() }
        // Render at up to 2× the screen width, so a zoomed page stays sharp without
        // re-rendering on every pinch.
        val renderWidth = (pageWidthPx * 2).coerceAtMost(2400)
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState(), enabled = reading)) {
            LazyColumn(
                state = list,
                userScrollEnabled = reading,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.width(pageWidth).fillMaxSize().padding(vertical = 8.dp),
            ) {
                items(pdf.count) { i -> PdfPage(pdf, i, renderWidth, tool, marks) }
            }
        }
        val page by remember { derivedStateOf { list.firstVisibleItemIndex + 1 } }
        Text(
            "$page / ${pdf.count}",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .background(Color(0x99000000), MaterialTheme.shapes.small)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun PdfPage(pdf: PdfDoc, index: Int, renderWidth: Int, tool: PdfTool, marks: MutableList<PdfMark>) {
    val size = remember(pdf, index) { pdf.size(index) }
    val bitmap by produceState<Bitmap?>(null, pdf, index, renderWidth) {
        value = pdf.render(index, renderWidth)
    }
    val scope = rememberCoroutineScope()
    val stroke = remember(index) { mutableStateListOf<PointF>() }
    var dragFrom by remember(index) { mutableStateOf<PointF?>(null) }
    var dragTo by remember(index) { mutableStateOf<PointF?>(null) }
    Box(
        Modifier.fillMaxWidth().aspectRatio(size.first / size.second).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b == null) CircularProgressIndicator(Modifier.padding(24.dp))
        else Image(b.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(tool, index) {
                    if (tool == PdfTool.NONE) return@pointerInput
                    // View pixels → page points.
                    fun pt(o: Offset) = PointF(o.x * size.first / this.size.width, o.y * size.second / this.size.height)
                    detectDragGestures(
                        onDragStart = { o ->
                            stroke.clear(); stroke.add(pt(o)); dragFrom = pt(o); dragTo = pt(o)
                        },
                        onDrag = { change, _ ->
                            val p = pt(change.position)
                            dragTo = p
                            if (tool == PdfTool.PEN) stroke.add(p)
                        },
                        onDragEnd = {
                            val a = dragFrom
                            val z = dragTo
                            if (tool == PdfTool.PEN && stroke.size > 1) marks.add(PdfMark.Ink(index, stroke.toList()))
                            if (tool == PdfTool.HIGHLIGHT && a != null && z != null) {
                                scope.launch {
                                    // The text lines between the two points where the page has
                                    // text (Android 15+), else the dragged box.
                                    val rects = pdf.selectionRects(index, a, z) ?: listOf(boxOf(a, z))
                                    if (rects.isNotEmpty()) marks.add(PdfMark.Highlight(index, rects))
                                }
                            }
                            stroke.clear(); dragFrom = null; dragTo = null
                        },
                        onDragCancel = { stroke.clear(); dragFrom = null; dragTo = null },
                    )
                },
        ) {
            val k = this.size.width / size.first
            fun o(p: PointF) = Offset(p.x * k, p.y * k)
            val ink = Color(PdfAnnotator.INK_RGB[0], PdfAnnotator.INK_RGB[1], PdfAnnotator.INK_RGB[2])
            val hl = Color(PdfAnnotator.HIGHLIGHT_RGB[0], PdfAnnotator.HIGHLIGHT_RGB[1], PdfAnnotator.HIGHLIGHT_RGB[2], 0.45f)
            fun drawStroke(points: List<PointF>) {
                if (points.size < 2) return
                val path = Path().apply {
                    moveTo(o(points[0]).x, o(points[0]).y)
                    points.drop(1).forEach { val q = o(it); lineTo(q.x, q.y) }
                }
                drawPath(path, ink, style = Stroke(width = (PdfAnnotator.INK_WIDTH_PT * k).coerceAtLeast(2f)))
            }
            fun drawBox(r: RectF) = drawRect(hl, topLeft = Offset(r.left * k, r.top * k), size = Size(r.width() * k, r.height() * k))
            marks.filter { it.page == index }.forEach { m ->
                when (m) {
                    is PdfMark.Ink -> drawStroke(m.points)
                    is PdfMark.Highlight -> m.rects.forEach { drawBox(it) }
                }
            }
            if (tool == PdfTool.PEN) drawStroke(stroke.toList())
            val a = dragFrom
            val z = dragTo
            if (tool == PdfTool.HIGHLIGHT && a != null && z != null) drawBox(boxOf(a, z))
        }
    }
}

/** The box two drag points span, at least a line high so a sideways swipe still marks something. */
private fun boxOf(a: PointF, b: PointF): RectF {
    val top = minOf(a.y, b.y)
    val bottom = maxOf(maxOf(a.y, b.y), top + 12f)
    return RectF(minOf(a.x, b.x), top, maxOf(a.x, b.x), bottom)
}

/** A [PdfRenderer] opens one page at a time and isn't thread-safe: every use holds the same lock. */
private class PdfDoc(private val fd: ParcelFileDescriptor, private val renderer: PdfRenderer) {
    private var closed = false
    private val ratios = HashMap<Int, Pair<Float, Float>>()
    val count: Int = renderer.pageCount

    /** A page's size in points, as shown — its slot gets the right height before it is drawn. */
    fun size(index: Int): Pair<Float, Float> = synchronized(ratios) {
        ratios.getOrPut(index) {
            if (closed) return@getOrPut 595f to 842f
            renderer.openPage(index).use { it.width.toFloat().coerceAtLeast(1f) to it.height.toFloat().coerceAtLeast(1f) }
        }
    }

    /**
     * The line boxes of the text between two points of a page, in page points — what a highlight
     * should cover. Null where the platform can't say (before Android 15, or a page that is only
     * an image), and the caller falls back to the dragged box.
     */
    @SuppressLint("NewApi")
    suspend fun selectionRects(index: Int, from: PointF, to: PointF): List<RectF>? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 35) return@withContext null
        synchronized(ratios) {
            if (closed) return@withContext null
            runCatching {
                renderer.openPage(index).use { page ->
                    val sel = page.selectContent(
                        SelectionBoundary(Point(from.x.toInt(), from.y.toInt())),
                        SelectionBoundary(Point(to.x.toInt(), to.y.toInt())),
                    )
                    sel?.selectedTextContents?.flatMap { it.bounds }?.filter { it.width() > 0f && it.height() > 0f }
                }
            }.getOrNull()?.takeIf { it.isNotEmpty() }
        }
    }

    suspend fun render(index: Int, width: Int): Bitmap? = withContext(Dispatchers.IO) {
        synchronized(ratios) {
            if (closed) return@withContext null
            renderer.openPage(index).use { page ->
                val height = (width.toFloat() * page.height / page.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(AndroidColor.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }

    fun close() = synchronized(ratios) {
        if (closed) return@synchronized
        closed = true
        runCatching { renderer.close() }
        runCatching { fd.close() }
    }
}
