package com.knotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.knotes.R
import com.knotes.gallery.AnnotationBurner
import com.knotes.gallery.AnnotationStyle
import com.knotes.gallery.Mark
import com.knotes.gallery.Renamer
import com.knotes.gallery.box
import com.knotes.gallery.normalise
import com.knotes.gallery.uprightSize
import com.knotes.player.isImageFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Marking tool. [NONE] is the default and leaves the gallery a plain viewer.
 *
 * A tool has to be armed because a horizontal drag can only mean one thing at a time, and on a
 * phone that gesture is already how you turn the page. Arming is one tap — the same as the desktop
 * annotator's toolbar button or Alt+P — and nothing else stands between you and drawing: there is
 * no editor screen to enter and no save button to press.
 */
private enum class Tool(@StringRes val labelRes: Int) {
    NONE(R.string.widgets_gallery_tool_view),
    PEN(R.string.widgets_gallery_tool_pen),
    HIGHLIGHT(R.string.widgets_gallery_tool_highlight),
    CROP(R.string.widgets_gallery_tool_crop),
}

/**
 * Full-screen, swipeable photo viewer with the desktop annotator's tools on it: pen, highlighter,
 * crop and rename. The delete button removes the current photo **immediately, without
 * confirmation** — a fast workflow for culling images.
 *
 * **Autosave, no save button** — ported from `scr_annotator.commit`. Marks and the label are written
 * when you leave the image (page change, disarming the tool, or leaving the gallery), marks first
 * because they are burnt into the file the label is about to rename. A failed save keeps the marks
 * in hand and says so rather than advancing: silently moving on from a failed save is how work
 * disappears.
 */
@Composable
fun GalleryScreen(
    dir: String,
    /**
     * Opened on one file, from a note's attachments or a server record's files (see
     * [openFileInApp]) rather than as the Gallery page. Then the pages are that folder's own
     * images only (not its subfolders, where the backups go), the start is [startFile], and the
     * delete asks first (a note's file is not a photo to cull). A rename or delete is reported
     * through [InAppViewer.changed] so the note list re-reads the folder.
     */
    startFile: File? = null,
    /** Shows a close button and takes Back; the pending marks are saved first. */
    onClose: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val attachments = startFile != null
    fun list() = if (attachments) listFolderImages(dir) else listImages(dir)
    var images by remember(dir) { mutableStateOf(list()) }

    if (images.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.widgets_gallery_no_images, dir),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    // Resume on the image last viewed in this folder — by path, not raw index, so it's unaffected
    // by photos added/removed since, and survives navigating away and a full app restart.
    val pagerState = rememberPagerState(
        initialPage = remember(dir) {
            (if (attachments) startFile?.absolutePath else lastViewedPath(context, dir))
                ?.let { saved -> images.indexOfFirst { it.absolutePath == saved } }
                ?.takeIf { it >= 0 } ?: 0
        },
        pageCount = { images.size },
    )

    // A file opened from a note is opened to be marked: the pen is ready. The Gallery page starts
    // in View, where a swipe turns the page.
    var tool by remember { mutableStateOf(if (startFile != null) Tool.PEN else Tool.NONE) }
    var label by remember { mutableStateOf("") }
    val marks = remember { mutableStateListOf<Mark>() }
    var error by remember { mutableStateOf<String?>(null) }

    // The image the pending marks belong to. Held separately from pagerState.currentPage because
    // the save happens AFTER the page has already moved on.
    var markedFile by remember { mutableStateOf<File?>(null) }
    var closeAction: (() -> Unit)? = null
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    // Autosave. The picture as it was when marking began is copied aside (sessionBase); every save
    // draws ALL the marks so far onto that copy and writes the result over the file. So a JPEG is
    // re-encoded from the same base each time (no loss piling up stroke after stroke), and an undo
    // after a save takes the stroke out of the file too. burnedCount = marks already in the file.
    var sessionBase by remember { mutableStateOf<File?>(null) }
    var burnedCount by remember { mutableIntStateOf(0) }

    suspend fun startSession(file: File) {
        if (sessionBase != null) return
        sessionBase = withContext(Dispatchers.IO) {
            runCatching {
                File(context.cacheDir, "annot-base").apply { mkdirs() }
                    .let { File(it, file.name) }
                    .also { file.copyTo(it, overwrite = true) }
            }.getOrNull()
        }
    }

    /** Writes the marks in hand into the file now; true when the file matches them. */
    suspend fun burnNow(): Boolean {
        val file = markedFile ?: return true
        val base = sessionBase ?: return marks.isEmpty()
        val now = marks.toList()
        if (now.size == burnedCount) return true
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                // Every stroke undone after a save: put the picture back as it was.
                if (now.isEmpty()) { base.copyTo(file, overwrite = true); true }
                else AnnotationBurner.burn(file, now, source = base)
            }.getOrDefault(false)
        }
        if (ok) {
            burnedCount = now.size
            if (now.isNotEmpty()) InAppViewer.reviewed++
        }
        return ok
    }

    LaunchedEffect(Unit) {
        snapshotFlow { marks.size }.collectLatest { n ->
            if (n != burnedCount) {
                delay(1500)
                if (!burnNow()) error = context.getString(R.string.widgets_gallery_save_failed_marks_kept)
            }
        }
    }

    suspend fun commit(): Boolean {
        val file = markedFile ?: return true
        val pendingLabel = label
        if (marks.isEmpty() && burnedCount == 0 && pendingLabel.isBlank()) return true

        // Marks before the rename: they are burnt into the file the label is about to move, so
        // the other order would write to a path that no longer exists.
        if (!burnNow()) return false
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                Renamer.rename(file, file.name.substringBeforeLast('.'), pendingLabel)
                true
            }.getOrDefault(false)
        }
        if (ok) {
            if (attachments && pendingLabel.isNotBlank()) InAppViewer.changed++
            marks.clear()
            burnedCount = 0
            sessionBase?.delete()
            sessionBase = null
            label = ""
            markedFile = null
            images = list()
        }
        return ok
    }

    LaunchedEffect(dir) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (!attachments) images.getOrNull(page)?.let { saveLastViewedPath(context, dir, it.absolutePath) }
            // Arriving at a new page: whatever is in hand belongs to the one just left.
            if (marks.isNotEmpty() || burnedCount > 0 || label.isNotBlank()) {
                error = if (commit()) null else context.getString(R.string.widgets_gallery_save_failed_marks_kept)
            }
        }
    }
    if (onClose != null) {
        val close: () -> Unit = {
            scope.launch {
                if (commit()) onClose() else error = context.getString(R.string.widgets_gallery_save_failed_marks_kept)
            }
        }
        BackHandler(onBack = close)
        closeAction = close
    }
    DisposableEffect(dir) {
        onDispose {
            // Leaving the gallery entirely. Fire-and-forget — there is no UI left to report to,
            // but dropping the work silently would be worse.
            scope.launch { commit() }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            // A drag can't both draw and turn the page. With a tool armed the drag draws, and the
            // arrows page instead.
            userScrollEnabled = tool == Tool.NONE,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val file = images.getOrNull(page)
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    // While marking, the picture as it was before the session: the marks are drawn
                    // over it live, and the file itself already has them burnt in by the autosave.
                    model = sessionBase?.takeIf { file != null && file == markedFile } ?: file,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                // Only the visible page is drawable, so a mark can't land on a neighbour mid-swipe.
                if (file != null && page == pagerState.currentPage && tool != Tool.NONE) {
                    MarkingLayer(file, tool, marks) {
                        if (markedFile == null) markedFile = file
                        scope.launch { startSession(file) }
                    }
                }
            }
        }

        Text(
            "${(pagerState.currentPage + 1).coerceAtMost(images.size)} / ${images.size}",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .fillMaxWidth()
                // Sideways scroll rather than squeezing: on a narrow screen the last chip was
                // crushed to one letter per line.
                .horizontalScroll(rememberScrollState())
                .padding(start = 8.dp, end = 8.dp, top = 34.dp),
        ) {
            Tool.entries.forEach { t ->
                FilterChip(
                    selected = tool == t,
                    onClick = {
                        // Disarming is a natural "done with this one" moment — save then too.
                        if (t == Tool.NONE && (marks.isNotEmpty() || label.isNotBlank())) {
                            scope.launch {
                                error = if (commit()) null else context.getString(R.string.widgets_gallery_save_failed)
                            }
                        }
                        tool = t
                    },
                    label = { Text(stringResource(t.labelRes), style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false) },
                    leadingIcon = {
                        Icon(
                            when (t) {
                                Tool.NONE -> Icons.Filled.PanTool
                                Tool.PEN -> Icons.Filled.Brush
                                Tool.HIGHLIGHT -> Icons.Filled.FormatColorFill
                                Tool.CROP -> Icons.Filled.Crop
                            },
                            null,
                            modifier = Modifier.width(15.dp),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Black.copy(alpha = 0.45f),
                        labelColor = Color.White,
                        iconColor = Color.White,
                    ),
                )
            }
            if (marks.isNotEmpty()) {
                IconButton(onClick = { marks.removeAt(marks.lastIndex) }) {
                    Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.widgets_gallery_undo_mark), tint = Color.White)
                }
            }
        }

        // Paging buttons. ALWAYS shown, not only while a tool is armed: they are the only way
        // forward once a tool has taken the swipe, and a control that appears and disappears is one
        // you have to hunt for at the moment you need it most. Solid dark discs rather than bare
        // tinted icons — a translucent glyph vanishes against a light photo, which is exactly the
        // situation you are in while marking one up.
        //
        // Moving pages is also what triggers the autosave (see commit), so these are the save
        // button in disguise; that is another reason not to hide them.
        PageButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            description = stringResource(R.string.widgets_gallery_previous),
            enabled = pagerState.currentPage > 0,
            alignment = Alignment.CenterStart,
            onClick = {
                scope.launch {
                    pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                }
            },
        )
        PageButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            description = stringResource(R.string.widgets_gallery_next),
            enabled = pagerState.currentPage < images.lastIndex,
            alignment = Alignment.CenterEnd,
            onClick = {
                scope.launch {
                    pagerState.animateScrollToPage(
                        (pagerState.currentPage + 1).coerceAtMost(images.lastIndex),
                    )
                }
            },
        )

        closeAction?.let { close ->
            IconButton(
                onClick = close,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 4.dp)
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
            ) {
                Icon(Icons.Filled.Close, stringResource(R.string.viewer_close), tint = Color.White)
            }
        }

        OutlinedTextField(
            value = label,
            onValueChange = {
                label = it
                if (markedFile == null) markedFile = images.getOrNull(pagerState.currentPage)
            },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.widgets_gallery_label_hint), color = Color.White.copy(alpha = 0.6f)) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Black.copy(alpha = 0.5f),
                unfocusedContainerColor = Color.Black.copy(alpha = 0.5f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 96.dp, bottom = 20.dp)
                .fillMaxWidth(),
        )

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(12.dp),
            )
        }

        fun deleteCurrent() {
            val idx = pagerState.currentPage
            images.getOrNull(idx)?.let { file ->
                file.delete()
                // Anything pending belonged to the file just deleted.
                marks.clear()
                burnedCount = 0
                sessionBase?.delete()
                sessionBase = null
                markedFile = null
                images = images.toMutableList().also { it.removeAt(idx) }
                if (attachments) {
                    InAppViewer.changed++
                    // The folder's last image gone: nothing left to show.
                    if (images.isEmpty()) onClose?.invoke()
                } else {
                    images.getOrNull(idx.coerceAtMost(images.lastIndex))
                        ?.let { next -> saveLastViewedPath(context, dir, next.absolutePath) }
                }
            }
        }
        confirmDelete?.let { name ->
            AlertDialog(
                onDismissRequest = { confirmDelete = null },
                title = { Text(stringResource(R.string.viewer_delete_title)) },
                text = { Text(name) },
                confirmButton = {
                    TextButton(onClick = { confirmDelete = null; deleteCurrent() }) {
                        Text(stringResource(R.string.viewer_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = null }) { Text(stringResource(android.R.string.cancel)) }
                },
            )
        }

        FloatingActionButton(
            onClick = {
                // The Gallery page culls on one tap; a note's file asks first.
                if (attachments) confirmDelete = images.getOrNull(pagerState.currentPage)?.name
                else deleteCurrent()
            },
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.widgets_gallery_delete_photo))
        }
    }
}

/**
 * A page-turn button: a solid disc so it stays visible over any photo, with a touch target big
 * enough to hit without looking. Dimmed rather than removed at the ends of the folder, so the
 * control never moves.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.PageButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    alignment: Alignment,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .align(alignment)
            .padding(horizontal = 6.dp)
            .size(52.dp)
            .background(Color.Black.copy(alpha = if (enabled) 0.45f else 0.2f), CircleShape),
    ) {
        Icon(
            icon,
            description,
            tint = Color.White.copy(alpha = if (enabled) 0.95f else 0.35f),
            modifier = Modifier.size(30.dp),
        )
    }
}

/**
 * The transparent drawing surface over the photo: collects marks and previews them.
 *
 * Marks are stored in IMAGE pixels, converted as they are drawn. The picture is letterboxed and is
 * usually far larger than the screen, so a stroke recorded in view pixels would burn in the wrong
 * place and at the wrong thickness. The image's dimensions come from its header alone — decoding
 * every page's pixels just to map a touch would be wasteful.
 */
@Composable
private fun MarkingLayer(
    file: File,
    tool: Tool,
    marks: MutableList<Mark>,
    onFirstMark: () -> Unit,
) {
    val dims = remember(file) { uprightSize(file) } ?: return
    val (imageW, imageH) = dims

    val current = remember(file) { mutableStateListOf<Pair<Float, Float>>() }
    var dragStart by remember(file) { mutableStateOf<Pair<Float, Float>?>(null) }
    var dragNow by remember(file) { mutableStateOf<Pair<Float, Float>?>(null) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(tool, file) {
                detectDragGestures(
                    onDragStart = { off ->
                        val p = viewToImage(off, size.toFloatSize(), imageW, imageH)
                        dragStart = p
                        dragNow = p
                        current.clear()
                        if (tool == Tool.PEN) current.add(p)
                    },
                    onDrag = { change, _ ->
                        val p = viewToImage(change.position, size.toFloatSize(), imageW, imageH)
                        dragNow = p
                        if (tool == Tool.PEN) current.add(p)
                    },
                    onDragEnd = {
                        val a = dragStart
                        val b = dragNow
                        when {
                            tool == Tool.PEN && current.size > 1 ->
                                marks.add(Mark.Pen(current.toList()))
                            // The drag sets only the x extent; the band's thickness is fixed, like
                            // a real highlighter nib.
                            tool == Tool.HIGHLIGHT && a != null && b != null ->
                                marks.add(Mark.Highlight(a.first, b.first, b.second))
                            tool == Tool.CROP && a != null && b != null ->
                                marks.add(Mark.Crop(a.first, a.second, b.first, b.second))
                        }
                        if (marks.isNotEmpty()) onFirstMark()
                        current.clear()
                        dragStart = null
                        dragNow = null
                    },
                    onDragCancel = { current.clear(); dragStart = null; dragNow = null },
                )
            }
            .pointerInput(tool, file) {
                detectTapGestures { off ->
                    if (tool == Tool.PEN) {
                        marks.add(Mark.Pen(listOf(viewToImage(off, size.toFloatSize(), imageW, imageH))))
                        onFirstMark()
                    }
                }
            },
    ) {
        val fit = fitRect(size, imageW, imageH)
        val scale = fit.width / imageW
        marks.forEach { drawMark(it, fit.left, fit.top, scale) }
        val a = dragStart
        val b = dragNow
        when {
            tool == Tool.PEN && current.size > 1 ->
                drawMark(Mark.Pen(current.toList()), fit.left, fit.top, scale)
            tool == Tool.HIGHLIGHT && a != null && b != null ->
                drawMark(Mark.Highlight(a.first, b.first, b.second), fit.left, fit.top, scale)
            tool == Tool.CROP && a != null && b != null ->
                drawMark(Mark.Crop(a.first, a.second, b.first, b.second), fit.left, fit.top, scale)
        }
    }
}

private fun androidx.compose.ui.unit.IntSize.toFloatSize() = Size(width.toFloat(), height.toFloat())

/** Where the letterboxed image sits inside the canvas, in view pixels. */
private data class Fit(val left: Float, val top: Float, val width: Float, val height: Float)

private fun fitRect(canvas: Size, imageW: Int, imageH: Int): Fit {
    val scale = minOf(canvas.width / imageW, canvas.height / imageH)
    val w = imageW * scale
    val h = imageH * scale
    return Fit((canvas.width - w) / 2f, (canvas.height - h) / 2f, w, h)
}

/** View pixels → image pixels: the inverse of the letterboxing above. */
private fun viewToImage(p: Offset, canvas: Size, imageW: Int, imageH: Int): Pair<Float, Float> {
    val fit = fitRect(canvas, imageW, imageH)
    val scale = fit.width / imageW
    return ((p.x - fit.left) / scale) to ((p.y - fit.top) / scale)
}

/** Draws one mark in view coordinates — a preview of what the burner will paint at full size. */
private fun DrawScope.drawMark(mark: Mark, left: Float, top: Float, scale: Float) {
    fun vx(x: Float) = left + x * scale
    fun vy(y: Float) = top + y * scale

    when (mark) {
        is Mark.Pen -> {
            val pts = mark.points
            val colour = Color(AnnotationStyle.PEN_COLOR)
            val width = (AnnotationStyle.PEN_WIDTH_PX * scale).coerceAtLeast(1.5f)
            if (pts.size == 1) {
                drawCircle(colour, width / 2, Offset(vx(pts[0].first), vy(pts[0].second)))
            } else if (pts.size > 1) {
                val path = Path().apply {
                    moveTo(vx(pts[0].first), vy(pts[0].second))
                    pts.drop(1).forEach { lineTo(vx(it.first), vy(it.second)) }
                }
                drawPath(path, colour, style = Stroke(width = width))
            }
        }
        is Mark.Highlight -> {
            val (x0, y0, x1, y1) = mark.box()
            drawRect(
                Color(AnnotationStyle.HL_COLOR).copy(alpha = AnnotationStyle.HL_ALPHA),
                Offset(vx(x0), vy(y0)),
                Size((x1 - x0) * scale, (y1 - y0) * scale),
            )
        }
        is Mark.Crop -> {
            val (x0, y0, x1, y1) = normalise(mark.x0, mark.y0, mark.x1, mark.y1)
            // An outline only — the picture is not cropped until the autosave burns it.
            drawRect(
                Color.White,
                Offset(vx(x0), vy(y0)),
                Size((x1 - x0) * scale, (y1 - y0) * scale),
                style = Stroke(width = 2f),
            )
        }
    }
}

/** All images under [dir] and its subfolders, ordered by path so folders stay grouped. */
private fun listImages(dir: String): List<File> =
    File(dir).walkTopDown()
        .filter { it.isFile && isImageFile(it.name) }
        .sortedBy { it.absolutePath.lowercase() }
        .toList()

/** The images directly in [dir], by name: a note's or record's files, without the backups below. */
private fun listFolderImages(dir: String): List<File> =
    File(dir).listFiles()
        ?.filter { it.isFile && !it.name.startsWith(".") && isImageFile(it.name) }
        ?.sortedBy { it.name.lowercase() }
        .orEmpty()

// Last-viewed image per gallery folder, keyed by [dir] so switching the configured folder in
// Settings doesn't clobber where you were in a previous one.
private const val GALLERY_PREFS = "knotes_gallery"

private fun lastViewedPath(context: Context, dir: String): String? =
    context.getSharedPreferences(GALLERY_PREFS, Context.MODE_PRIVATE).getString(dir, null)

private fun saveLastViewedPath(context: Context, dir: String, path: String) {
    context.getSharedPreferences(GALLERY_PREFS, Context.MODE_PRIVATE).edit().putString(dir, path).apply()
}
