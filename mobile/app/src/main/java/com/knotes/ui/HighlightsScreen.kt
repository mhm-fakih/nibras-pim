package com.knotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knotes.flash.FlashCache
import com.knotes.flash.FlashcardStore
import com.knotes.flash.Highlight
import com.knotes.util.Clip
import com.knotes.util.FriendlyTime
import com.knotes.util.grouped
import com.knotes.util.Markdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Your records' key points, one record at a time.
 *
 * Read-only and LIVE, which is what makes it a different page from the flash cards: that is a
 * frozen job with decisions attached, this is simply what the records currently say. Filling in a
 * record's highlights puts it here; there is nothing to decide and nothing is written back.
 *
 * Every module that carries the field feeds this — notes, tasks, journals, planners, goals,
 * writings and resources — so each card is named with the record it came from.
 *
 * The bullets arrive already split by the server, so this screen and the web page cannot disagree
 * about what counts as a line.
 *
 * **Works with no server.** The cache is shown FIRST and the network is only a refresh on top of
 * it: reading happens away from the desk, and a gallery that answers "the server is offline" is
 * useless exactly when it is wanted. "Live" means it follows the records whenever it can reach
 * them, not that it is worthless when it cannot.
 */
@Composable
fun HighlightsScreen(store: FlashcardStore, baseUrl: String) {
    val context = LocalContext.current
    val api = remember { com.knotes.nibras.NibrasApi() }
    val cache = remember { FlashCache(context) }
    var items by remember { mutableStateOf<List<Highlight>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var offlineSince by remember { mutableStateOf<Long?>(null) }
    var fontScale by remember { mutableStateOf(store.highlightFontScale) }

    LaunchedEffect(baseUrl) {
        // The saved copy first, before the network is even tried. On a slow or absent connection
        // this is the difference between a gallery and a spinner.
        val cached = withContext(Dispatchers.IO) { cache.highlights() }
        if (cached != null && items == null) items = cached

        withContext(Dispatchers.IO) {
            runCatching { api.fetchHighlightsRaw(baseUrl) }
                .onSuccess { raw ->
                    cache.saveRaw(FlashCache.HIGHLIGHTS, raw)
                    val fresh = cache.highlights().orEmpty()
                    withContext(Dispatchers.Main) {
                        items = fresh; error = null; offlineSince = null
                    }
                }
                .onFailure { e ->
                    val savedAt = cache.savedAt(FlashCache.HIGHLIGHTS)
                    withContext(Dispatchers.Main) {
                        if (cached == null) {
                            // Nothing saved and nothing reachable: this is the one case where the
                            // page genuinely has nothing, and it should say why rather than look
                            // like an empty gallery.
                            items = emptyList()
                            error = e.message?.takeIf { m -> m.isNotBlank() }
                                ?: "Could not reach the server, and nothing is saved on this phone yet."
                        } else {
                            offlineSince = savedAt
                        }
                    }
                }
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        val list = items
        if (list == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val pager = rememberPagerState(pageCount = { list.size })
        val index = if (list.isEmpty()) 0 else pager.currentPage.coerceIn(0, list.lastIndex)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    list.isEmpty() -> "Highlights"
                    // Named, not hidden: cards that are days old should say so, or the gallery
                    // quietly claims to be current when it is not.
                    offlineSince != null ->
                        "${grouped(index + 1)} of ${grouped(list.size)} · saved copy, ${FriendlyTime.ago(offlineSince!!)}"
                    else -> "${grouped(index + 1)} of ${grouped(list.size)} · records with highlights"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { fontScale = (fontScale - 0.1f).coerceAtLeast(FlashcardStore.MIN_FONT).also { store.highlightFontScale = it } },
                enabled = fontScale > FlashcardStore.MIN_FONT,
                modifier = Modifier.size(30.dp),
            ) { Icon(Icons.Filled.Remove, "Smaller text", modifier = Modifier.size(17.dp)) }
            Text("A", style = MaterialTheme.typography.labelMedium)
            IconButton(
                onClick = { fontScale = (fontScale + 0.1f).coerceAtMost(FlashcardStore.MAX_FONT).also { store.highlightFontScale = it } },
                enabled = fontScale < FlashcardStore.MAX_FONT,
                modifier = Modifier.size(30.dp),
            ) { Icon(Icons.Filled.Add, "Larger text", modifier = Modifier.size(17.dp)) }
        }
        HorizontalDivider()

        if (list.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Text(
                    error ?: "No record has its highlights filled in yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            pageSpacing = 10.dp,
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) { page ->
            list.getOrNull(page)?.let { HighlightFace(it, fontScale) }
        }
    }
}

@Composable
private fun HighlightFace(item: Highlight, fontScale: Float) {
    val body = MaterialTheme.typography.bodyMedium
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.recordRef,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // The whole card's bullets, as the "- " list they came from, so what is pasted into a
            // note is a list again rather than a paragraph of run-together lines.
            val copyContext = LocalContext.current
            IconButton(
                onClick = {
                    Clip.copy(
                        copyContext,
                        item.recordRef,
                        item.bullets.joinToString("\n") { b -> "- $b" },
                    )
                },
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "Copy these highlights",
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (item.subtitle.isNotBlank() || item.date.isNotBlank()) {
            Text(
                listOf(item.subtitle, item.date).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(10.dp))
        /*
         * These scroll, unlike a flash card.
         *
         * A card is one passage of at most a hundred words and is shrunk to fit, so it is always
         * whole. A record's highlights are a list of unknown length — twenty bullets is ordinary —
         * and shrinking that to fit would end at three points of unreadable type. A list is the one
         * shape where scrolling is the honest answer.
         */
        // One container around the whole list, not one per bullet, so a selection can run across
        // several bullets in a single drag — which is how a list is quoted.
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                item.bullets.forEach { bullet ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = if (item.rtl) Arrangement.End else Arrangement.Start,
                    ) {
                        // The marker follows the text's own direction, or an Arabic list hangs its
                        // bullets off the wrong edge.
                        if (!item.rtl) {
                            Text("•", style = body, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            // Styled, not stripped: a resource's highlights field is Markdown, and
                            // shown raw the bold ones read as "**Governing principle:**". The same
                            // Markdown.spans a note is read through, so a bullet looks here the way
                            // it does on the record's own screen.
                            markdownStyled(bullet),
                            style = body.copy(
                                textDirection = TextDirection.Content,
                                fontSize = body.fontSize * fontScale,
                                lineHeight = body.fontSize * fontScale * 1.6f,
                            ),
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (item.rtl) {
                            Spacer(Modifier.width(8.dp))
                            Text("•", style = body, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

/** One bullet with its Markdown emphasis applied; the markers stay, faded, as they do in a note. */
private fun markdownStyled(text: String): AnnotatedString = buildAnnotatedString {
    append(text)
    Markdown.spans(text).forEach { addStyle(it.style, it.start, it.end) }
}
