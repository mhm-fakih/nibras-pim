package com.knotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.knotes.Note
import com.knotes.util.Bidi
import com.knotes.util.Clauses
import com.knotes.util.Highlights
import com.knotes.util.Markdown
import kotlin.math.abs

/**
 * Read one note, tapping clauses to highlight them — the local-file counterpart of the Resources
 * full-text reader.
 *
 * <h3>Highlights live in the note file</h3>
 * A tapped clause is wrapped in `==…==` **in the note itself**, not recorded in a phone-local set.
 * That is the difference between this and the Nibras resource reader, and it follows from where
 * the thing being read lives: a resource is a server record the phone only mirrors, so its
 * highlight has to be queued and pushed. A note *is* a file in a synced folder — so writing the
 * marker into the file means the highlight travels to the desktop and the backend by the same route
 * the note itself does, with nothing to sync, queue, or reconcile. It is also the same `==…==`
 * markdown the web client writes, so a note highlighted here reads correctly there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteReadScreen(
    note: Note,
    settings: com.knotes.AppSettings,
    /** Persists the note's new body — after a highlight is toggled, or while typing. */
    onSave: (String) -> Unit,
    /**
     * Tags changed: the CURRENT body plus the new type/priority/category. The body rides along
     * because saving tags rewrites the file, and anything typed but not yet written would be lost.
     */
    onTags: (body: String, type: String?, priority: Int, category: String?) -> Unit,
    onBack: () -> Unit,
    /** Open straight into edit mode — what the card's Edit button wants. */
    startInEdit: Boolean = false,
    /** Reports a Read⇄Edit switch, so the caller can reopen in the same mode after a rename. */
    onModeChange: (Boolean) -> Unit = {},
    /**
     * Hide every control that writes. Used by the AI-answers page, whose folder is Syncthing's:
     * an edit made here would be overwritten on the next sync, or worse, would win and diverge from
     * the desktop. Passing no-op save/tag callbacks alone is not enough — that leaves buttons that
     * appear to work and silently do nothing.
     */
    readOnly: Boolean = false,
) {
    // Read and edit are two views of one screen, as on the desktop's side pane: the same note, the
    // same full height, one tap apart. Editing in a six-line bar at the bottom of the list was the
    // thing that made a long note unworkable.
    var editing by remember(note.file.absolutePath) { mutableStateOf(startInEdit && !readOnly) }
    // The body as edited so far, so successive taps compose instead of each starting from the
    // note as it was when the screen opened.
    var source by remember(note.file.absolutePath) { mutableStateOf(note.content) }

    val plain = remember(source) { Highlights.strip(source) }
    val marked = remember(source) { Highlights.markedTexts(source) }
    val spans = remember(plain) { Clauses.clauseSpans(plain) }

    val highlightColor = MaterialTheme.colorScheme.tertiaryContainer
    val annotated = remember(plain, spans, marked, highlightColor, note.isRtl) {
        buildAnnotatedString {
            append(plain)
            // Direction per LINE, from its first strong letter — a note that mixes an English
            // heading with an Arabic quotation gets both right. Justified in the same pass.
            Bidi.lines(plain, fallbackRtl = note.isRtl).forEach {
                addStyle(Bidi.paragraphStyle(it.rtl), it.start, it.end)
            }
            // Markdown FIRST, highlights after: a highlighted clause that also happens to be bold
            // should read as both, and the later style wins where they set the same property.
            //
            // The markers are styled in place rather than stripped — every character keeps its
            // offset, so the clause spans and the tap hit-testing still line up with the text they
            // were computed from. Removing "**" would shift everything after it and highlight the
            // wrong words.
            Markdown.spans(plain).forEach { addStyle(it.style, it.start, it.end) }
            spans.forEach { s ->
                if (s.text in marked) {
                    addStyle(SpanStyle(background = highlightColor, fontWeight = FontWeight.Bold), s.start, s.end)
                }
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    // No direction/alignment on the base style: each line carries its own (see Bidi), and a style
    // set here would be the one that wins for lines the paragraph styles don't cover.
    val base = MaterialTheme.typography.bodyLarge
    val style = base.copy(lineHeight = base.fontSize * 1.7f)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        if (readOnly) "AI answer" else if (editing) "Edit" else "Read",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                actions = {
                    if (!readOnly) {
                        FilterChip(
                            selected = !editing,
                            onClick = { editing = false; onModeChange(false) },
                            label = { Text("Read") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                        Spacer(Modifier.width(6.dp))
                        FilterChip(
                            selected = editing,
                            onClick = { editing = true; onModeChange(true) },
                            label = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                },
            )
        },
        bottomBar = {
            // The tag bar renames the file. Nothing on a synced folder may do that from here.
            if (!readOnly) {
                NoteTagBar(
                    settings = settings,
                    type = note.type,
                    priority = note.priority ?: settings.priorityMin,
                    category = note.category,
                    onType = { onTags(source, it, note.priority ?: settings.priorityMin, note.category) },
                    onPriority = { onTags(source, note.type, it, note.category) },
                    onCategory = { onTags(source, note.type, note.priority ?: settings.priorityMin, it) },
                )
            }
        },
    ) { padding ->
        if (editing) {
            // The whole screen, minus the bar. A note long enough to be worth reading is long
            // enough that a fixed-height box is the wrong shape to edit it in.
            NoteEditor(
                source = source,
                onChange = { source = it },
                onCommit = { onSave(source) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Tap a clause to highlight it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "· Edit above · tags below",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                val n = marked.size
                if (n > 0) {
                    Text(
                        "$n highlighted",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.size(6.dp))
            HorizontalDivider()
            Spacer(Modifier.size(10.dp))

            Text(
                text = annotated,
                style = style,
                onTextLayout = { layout = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(spans) {
                        detectTapGestures { pos ->
                            val lr = layout ?: return@detectTapGestures
                            val offset = lr.getOffsetForPosition(pos)
                            // Nearest clause when the tap lands in the gap between two (a space, or
                            // past the end of a short last line) — a tap that does nothing reads as
                            // the feature being broken.
                            val hit = spans.firstOrNull { offset in it.start until it.end }
                                ?: spans.minByOrNull { minOf(abs(offset - it.start), abs(offset - (it.end - 1))) }
                                ?: return@detectTapGestures
                            val updated =
                                if (hit.text in marked) Highlights.unmark(source, hit.text)
                                else Highlights.mark(source, hit.text)
                            if (updated != source) {
                                source = updated
                                onSave(updated)
                            }
                        }
                    },
            )

            if (plain.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("This note is empty.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.size(32.dp))
        }
    }
}

/**
 * The note's text, filling whatever room the screen has.
 *
 * <h4>Saving</h4>
 * Typing updates the in-memory copy; the write happens when you pause (see the debounce) and again
 * when the editor leaves the composition — going Back, switching to Read, or opening another note.
 * Per-keystroke writes would rewrite the file on a synced folder dozens of times a sentence, which
 * Syncthing would faithfully propagate; waiting only for Back would lose the text if the process
 * were killed. Both together cost one write per pause.
 *
 * Direction follows the text: [Bidi] decides per line, so an English paragraph under an Arabic one
 * behaves while it is being typed, not only once it is saved.
 */
@Composable
private fun NoteEditor(
    source: String,
    onChange: (String) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Always the LATEST commit lambda, never the one captured when these effects started.
    //
    // This is not a formality. `onCommit` closes over the note being edited, and a tag change
    // renames the file — so the lambda captured at first composition points at a path that no
    // longer exists. `DisposableEffect(Unit)` never restarts, so without this it would fire that
    // stale lambda on the way out and write the body back to the OLD name, leaving two notes where
    // there was one. The debounce has the same trap by a narrower door: retagging without typing
    // leaves `source` byte-identical, so `LaunchedEffect(source)` doesn't restart either.
    val commit by rememberUpdatedState(onCommit)

    // Debounced write: restarts on every keystroke, so it fires once you stop.
    LaunchedEffect(source) {
        kotlinx.coroutines.delay(900)
        commit()
    }
    // And a final write when this leaves the screen, for text typed inside the debounce window.
    DisposableEffect(Unit) {
        onDispose { commit() }
    }

    OutlinedTextField(
        value = source,
        onValueChange = onChange,
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.5f,
            textDirection = if (com.knotes.NoteFiles.isRtl(source)) androidx.compose.ui.text.style.TextDirection.Rtl
            else androidx.compose.ui.text.style.TextDirection.Ltr,
        ),
        placeholder = { Text("This note is empty — start typing.") },
    )
}
