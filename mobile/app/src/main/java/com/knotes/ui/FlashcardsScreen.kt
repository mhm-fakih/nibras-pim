package com.knotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knotes.R
import com.knotes.flash.CardState
import com.knotes.flash.FlashCache
import com.knotes.flash.FlashOutbox
import com.knotes.flash.FlashSummary
import com.knotes.flash.Flashcard
import com.knotes.flash.FlashcardStore
import com.knotes.nibras.PushCenter
import com.knotes.util.Clip
import com.knotes.util.FriendlyTime
import com.knotes.util.grouped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class FlashTab { QUEUE, REPEAT, KEPT }

/**
 * Passages pulled out of long notes, shown ONE AT A TIME.
 *
 * A gallery, not a list: the whole screen carries a single passage, swiped through like pictures on
 * a wall. Reading something closely and scrolling a feed are different acts, and a list quietly
 * invites the second — the eye goes to the next card before the current one is finished.
 *
 * Nothing scrolls. A passage that will not fit is *shrunk to fit* (see [FittedPassage]) rather than
 * given a scrollbar, so every card is whole and the reader never has to work out whether there is
 * more below.
 *
 * The queue shows forty at a time and hides the rest — a bank of 22,000 is not something anyone can
 * face. The decided lists are uncapped: having said "come back to this", the app has no business
 * hiding the result.
 *
 * **Works with no server, decisions included.** The saved copy is shown first and the network is a
 * refresh on top of it; a decision taken offline moves the card, is written to the cache so it stays
 * moved across a restart, and waits in [FlashOutbox] until there is something to send it to. Cards
 * are read on a bus, and the whole point of the bank living in the backend is that a decision
 * reaches the browser eventually — not that it has to reach it this second.
 */
@Composable
fun FlashcardsScreen(store: FlashcardStore, baseUrl: String) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val api = remember { com.knotes.nibras.NibrasApi() }
    val cache = remember { FlashCache(context) }

    var queue by remember { mutableStateOf<List<Flashcard>?>(null) }
    var repeatList by remember { mutableStateOf<List<Flashcard>>(emptyList()) }
    var keptList by remember { mutableStateOf<List<Flashcard>>(emptyList()) }
    var summary by remember { mutableStateOf(FlashSummary()) }
    var error by remember { mutableStateOf<String?>(null) }
    var offlineSince by remember { mutableStateOf<Long?>(null) }
    var waiting by remember { mutableStateOf(0) }
    var tab by remember { mutableStateOf(runCatching { FlashTab.valueOf(store.tab) }.getOrDefault(FlashTab.QUEUE)) }
    var fontScale by remember { mutableStateOf(store.fontScale) }

    /** Put the three lists on disk, so what is on screen is what reopens. */
    fun persist() {
        cache.saveCards(CardState.NEW.name, queue.orEmpty())
        cache.saveCards(CardState.REPEAT.name, repeatList)
        cache.saveCards(CardState.KEPT.name, keptList)
    }

    suspend fun reload() {
        // Anything decided offline goes UP before anything comes down, or the server's answer would
        // overwrite the pending decision with the state it is about to be moved out of.
        withContext(Dispatchers.IO) { PushCenter.drainNow(context) }
        withContext(Dispatchers.IO) {
            runCatching {
                val q = api.fetchFlashcardsRaw(baseUrl, CardState.NEW.name)
                val r = api.fetchFlashcardsRaw(baseUrl, CardState.REPEAT.name)
                val k = api.fetchFlashcardsRaw(baseUrl, CardState.KEPT.name)
                val s = api.fetchFlashcardSummaryRaw(baseUrl)
                cache.saveRaw(FlashCache.cardsKey(CardState.NEW.name), q)
                cache.saveRaw(FlashCache.cardsKey(CardState.REPEAT.name), r)
                cache.saveRaw(FlashCache.cardsKey(CardState.KEPT.name), k)
                cache.saveRaw(FlashCache.SUMMARY, s)
                val lists = Triple(
                    cache.cards(CardState.NEW.name).orEmpty(),
                    cache.cards(CardState.REPEAT.name).orEmpty(),
                    cache.cards(CardState.KEPT.name).orEmpty(),
                )
                val sum = cache.summary() ?: FlashSummary()
                withContext(Dispatchers.Main) {
                    queue = lists.first; repeatList = lists.second; keptList = lists.third
                    summary = sum; error = null; offlineSince = null
                    waiting = FlashOutbox.pending.value.size
                }
            }.onFailure { e ->
                val cachedQueue = cache.cards(CardState.NEW.name)
                val savedAt = cache.savedAt(FlashCache.cardsKey(CardState.NEW.name))
                withContext(Dispatchers.Main) {
                    waiting = FlashOutbox.pending.value.size
                    if (cachedQueue == null && queue == null) {
                        queue = emptyList()
                        // Named rather than swallowed: an empty page and an unreachable server look
                        // identical otherwise, and only one of them is something you can act on.
                        error = (e.message?.takeIf { m -> m.isNotBlank() }
                            ?: context.getString(R.string.widgets_flash_no_server))
                    } else {
                        offlineSince = savedAt
                    }
                }
            }
        }
    }

    val tick by com.knotes.nibras.ServerRefresh.tick.collectAsState()
    LaunchedEffect(baseUrl, tick) {
        // The saved copy first, before the network is tried at all.
        val cached = withContext(Dispatchers.IO) {
            FlashOutbox.seed(context)
            Triple(
                cache.cards(CardState.NEW.name),
                cache.cards(CardState.REPEAT.name).orEmpty(),
                cache.cards(CardState.KEPT.name).orEmpty(),
            ) to cache.summary()
        }
        cached.first.first?.let { q ->
            queue = q
            repeatList = cached.first.second
            keptList = cached.first.third
            cached.second?.let { summary = it }
            waiting = FlashOutbox.pending.value.size
        }
        reload()
    }

    /**
     * Record a decision on the server, moving the card here as soon as it is sent.
     *
     * The lists are updated straight away rather than after the round trip: the reader is holding
     * the phone and the next passage should be there, not a spinner. A failed write puts the card
     * back and says so, so the optimism never quietly loses a decision.
     */
    fun decide(card: Flashcard, state: CardState) {
        val wasQueued = queue?.any { it.id == card.id } == true
        queue = queue?.filterNot { it.id == card.id }
        repeatList = repeatList.filterNot { it.id == card.id } +
            (if (state == CardState.REPEAT) listOf(card) else emptyList())
        keptList = keptList.filterNot { it.id == card.id } +
            (if (state == CardState.KEPT) listOf(card) else emptyList())
        if (wasQueued) summary = summary.copy(remaining = (summary.remaining - 1).coerceAtLeast(0))

        // Queued and written to disk BEFORE the network is touched. A decision the reader has made
        // is not the server's to accept or refuse — the only question is when it gets there, and a
        // card must not reappear in the queue because the phone was in a lift at the wrong moment.
        FlashOutbox.queue(context, card.id, state.name)
        persist()
        waiting = FlashOutbox.pending.value.size

        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { api.pushFlashcardState(baseUrl, card.id, state.name) }.getOrDefault(false)
            }
            if (ok) {
                FlashOutbox.remove(context, FlashOutbox.Pending(card.id, state.name))
                waiting = FlashOutbox.pending.value.size
                offlineSince = null
                if ((queue?.size ?: 0) < FlashBank.QUEUE_SIZE && summary.remaining > (queue?.size ?: 0)) {
                    // The server hands out a fixed working set; refill once this one has thinned.
                    reload()
                }
            } else {
                // Not an error and not put back — it is waiting, and the header says how many are.
                offlineSince = cache.savedAt(FlashCache.cardsKey(CardState.NEW.name))
            }
        }
    }

    fun undecide(card: Flashcard) = decide(card, CardState.NEW)

    // navigationBarsPadding as well as status: every other page in the app is either scrollable
    // with bottom padding or sits above the mini player bar, which carries that inset for them.
    // This page is neither — it is a fixed, full-height card whose action row lands exactly where
    // the navigation bar is, and without this the three buttons are drawn half underneath it.
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        if (queue == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val shown = when (tab) {
            FlashTab.QUEUE -> queue.orEmpty()
            FlashTab.REPEAT -> repeatList
            FlashTab.KEPT -> keptList
        }

        Column(modifier = Modifier.padding(horizontal = 10.dp)) {
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                TabChip(stringResource(R.string.widgets_flash_tab_review), queue.orEmpty().size, tab == FlashTab.QUEUE) {
                    tab = FlashTab.QUEUE; store.tab = tab.name
                }
                TabChip(stringResource(R.string.widgets_flash_tab_repeat), repeatList.size, tab == FlashTab.REPEAT) {
                    tab = FlashTab.REPEAT; store.tab = tab.name
                }
                TabChip(stringResource(R.string.widgets_flash_tab_kept), keptList.size, tab == FlashTab.KEPT) {
                    tab = FlashTab.KEPT; store.tab = tab.name
                }
            }
        }

        if (shown.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Text(
                    error ?: when (tab) {
                        FlashTab.QUEUE ->
                            if (summary.total == 0) stringResource(R.string.widgets_flash_empty_server)
                            else stringResource(R.string.widgets_flash_empty_queue)
                        FlashTab.REPEAT -> stringResource(R.string.widgets_flash_empty_repeat)
                        FlashTab.KEPT -> stringResource(R.string.widgets_flash_empty_kept)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        // One card per page. Deciding a card removes it from the list, the page count shrinks, and
        // the pager is already sitting on what was behind it — so the next passage simply arrives.
        val pager = rememberPagerState(pageCount = { shown.size })
        val index = pager.currentPage.coerceIn(0, shown.lastIndex)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.widgets_flash_position, grouped(index + 1), grouped(shown.size)) + when (tab) {
                    FlashTab.QUEUE -> " · " + stringResource(R.string.widgets_flash_left_of, grouped(summary.remaining), grouped(summary.total))
                    else -> ""
                } + offlineNote(offlineSince, waiting),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // The font controls live on THIS line, not up with the tabs. Sharing the tab row cost
            // the "+" button entirely on a 1080px screen: three chips at that width left the
            // spacer nothing to give, and the last control was simply pushed off the edge.
            IconButton(
                onClick = { fontScale = (fontScale - 0.1f).coerceAtLeast(FlashcardStore.MIN_FONT).also { store.fontScale = it } },
                enabled = fontScale > FlashcardStore.MIN_FONT,
                modifier = Modifier.size(30.dp),
            ) { Icon(Icons.Filled.Remove, stringResource(R.string.widgets_flash_smaller_text), modifier = Modifier.size(17.dp)) }
            Text("A", style = MaterialTheme.typography.labelMedium)
            IconButton(
                onClick = { fontScale = (fontScale + 0.1f).coerceAtMost(FlashcardStore.MAX_FONT).also { store.fontScale = it } },
                enabled = fontScale < FlashcardStore.MAX_FONT,
                modifier = Modifier.size(30.dp),
            ) { Icon(Icons.Filled.Add, stringResource(R.string.widgets_flash_larger_text), modifier = Modifier.size(17.dp)) }
        }
        HorizontalDivider()

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            pageSpacing = 10.dp,
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) { page ->
            shown.getOrNull(page)?.let { card ->
                CardFace(
                    card = card,
                    fontScale = fontScale,
                    tab = tab,
                    onDecide = { state -> decide(card, state) },
                    onUndecide = { undecide(card) },
                )
            }
        }
    }
}

@Composable
private fun CardFace(
    card: Flashcard,
    fontScale: Float,
    tab: FlashTab,
    onDecide: (CardState) -> Unit,
    onUndecide: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                card.recordRef,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                card.date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            topicLabel(card.topic)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Copies the passage alone — not the reference or the date. What is wanted elsewhere is
            // the words; a header line would have to be deleted at the other end every time.
            val copyContext = LocalContext.current
            IconButton(
                onClick = { Clip.copy(copyContext, card.recordRef, card.text) },
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.widgets_flash_copy_passage),
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // The passage owns the middle of the card and is centred in it, like a plate.
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            FittedPassage(card = card, fontScale = fontScale)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            /*
             * Reviewed sits on the RIGHT, and last.
             *
             * It is the action most cards get, and a phone held in the right hand has its thumb
             * already at that edge — the far side of the row is the awkward reach, so the rare
             * actions belong there. The web page uses the same order, so the two do not teach
             * opposite habits.
             *
             * Each button is as wide as its own label needs, rather than every button being the
             * width of the longest. An even split clipped "Reviewed" on a 720px screen; a fixed
             * bias toward it then ellipsised "Repeat" on a 1080px one. Sizing by label length
             * settles both, because it is the labels that differ, not the screens.
             */
            if (tab != FlashTab.QUEUE) {
                val unset = stringResource(R.string.widgets_flash_unset)
                ActionButton(Icons.AutoMirrored.Filled.Undo, unset, labelWeight(unset), onClick = onUndecide)
            }
            if (tab != FlashTab.KEPT) {
                val keep = stringResource(R.string.widgets_flash_keep)
                ActionButton(Icons.Filled.Bookmark, keep, labelWeight(keep)) { onDecide(CardState.KEPT) }
            }
            if (tab != FlashTab.REPEAT) {
                val repeat = stringResource(R.string.widgets_flash_repeat)
                ActionButton(Icons.Filled.Replay, repeat, labelWeight(repeat)) { onDecide(CardState.REPEAT) }
            }
            val reviewed = stringResource(R.string.widgets_flash_reviewed)
            ActionButton(Icons.Filled.CheckCircle, reviewed, labelWeight(reviewed), primary = true) {
                onDecide(CardState.REVIEWED)
            }
        }
    }
}

/**
 * The passage at the chosen size — or the largest size below it that fits the space.
 *
 * Compose 1.7 has no auto-sizing text, so this measures and steps down: [TextOverflow.Clip] plus a
 * bounded height makes `hasVisualOverflow` a reliable "did not fit", and each failed layout shrinks
 * the size a little until it does. It converges in a handful of passes and stops at a floor.
 *
 * The alternative was a scrollbar, and the whole point of showing one passage at a time is that
 * there is never anything below the fold to go looking for.
 */
@Composable
private fun FittedPassage(card: Flashcard, fontScale: Float) {
    val base = MaterialTheme.typography.bodyLarge
    // Re-measured from scratch whenever the passage or the chosen size changes.
    var scale by remember(card.id, fontScale) { mutableFloatStateOf(fontScale) }

    // Selectable, so a phrase can be lifted out without copying the whole card. The pager is
    // horizontal and so is a selection drag, so the two do compete: a plain swipe still turns the
    // page, and a long-press hands the gesture to the text. The copy button in the card's header
    // exists for the common case, where the whole passage is what is wanted anyway.
    SelectionContainer {
        Text(
            text = card.text,
            // Justified, and direction resolved from the passage's own first strong letter — so an
            // Arabic passage justifies to a clean right margin and an English one to the left, with
            // nothing to keep in step with the text. Justify rather than Start because a card is a
            // block of prose read closely; a ragged edge on 100 words in a narrow column is what
            // makes it look like a list item instead of a page.
            style = base.copy(
                textDirection = TextDirection.Content,
                textAlign = TextAlign.Justify,
                fontSize = base.fontSize * scale,
                lineHeight = base.fontSize * scale * 1.6f,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            overflow = TextOverflow.Clip,
            onTextLayout = { layout ->
                if (layout.hasVisualOverflow && scale > MIN_FITTED_SCALE) {
                    scale = (scale * 0.93f).coerceAtLeast(MIN_FITTED_SCALE)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Width in proportion to the label, plus a constant for the icon and padding every button carries.
 * Keeps the three buttons legible together on screens of very different widths without any of them
 * having to ellipsise.
 */
private fun RowScope.labelWeight(label: String): Modifier = Modifier.weight(label.length + 3f)

/** Below this the text is too small to be worth reading; a passage that still overflows is clipped. */
private const val MIN_FITTED_SCALE = 0.55f

/**
 * A filled, full-height decision button.
 *
 * Filled rather than bare text, and 52dp rather than the default: these are the three controls the
 * page exists for, pressed once per card and often without looking closely. As flat labels they
 * were a small target on a busy card — missed twice in testing on the device itself — and nothing
 * on screen said where one ended and the next began.
 */
@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = if (primary) {
        ButtonDefaults.buttonColors()
    } else {
        ButtonDefaults.filledTonalButtonColors()
    }
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = colors,
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        // Ellipsis rather than clip: on a narrower phone than this was tuned for, a label that
        // still will not fit should say so instead of quietly losing its last letter.
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TabChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(stringResource(R.string.widgets_flash_tab_count, label, grouped(count)), style = MaterialTheme.typography.labelSmall,
                maxLines = 1, softWrap = false)
        },
    )
}

@Composable
private fun topicLabel(topic: String): String? = when (topic) {
    "r" -> stringResource(R.string.widgets_flash_topic_religious)
    "p" -> stringResource(R.string.widgets_flash_topic_psychological)
    else -> null
}

/** The server hands out this many undecided cards at a time; the page refills when it thins. */
private object FlashBank { const val QUEUE_SIZE = 40 }

/**
 * The tail of the counter line when the server could not be reached: how old this copy is, and how
 * many decisions are still to go out.
 *
 * Both, or neither is much use. "Saved copy" alone leaves you wondering whether what you just
 * decided was kept; a waiting count alone does not say whether the cards themselves are current.
 * Empty string when everything is up to date, so the line reads normally in the ordinary case.
 */
@Composable
private fun offlineNote(savedAt: Long?, waiting: Int): String {
    val savedText = savedAt?.let { stringResource(R.string.widgets_flash_saved_copy, FriendlyTime.ago(LocalContext.current, it)) }
    val waitingText = if (waiting > 0) pluralStringResource(R.plurals.widgets_flash_decisions_to_sync, waiting, waiting) else null
    val parts = listOfNotNull(savedText, waitingText)
    return if (parts.isEmpty()) "" else " · " + parts.joinToString(" · ")
}
