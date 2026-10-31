package com.knotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.knotes.R
import com.knotes.nibras.NIBRAS_TYPES
import kotlinx.coroutines.launch

/** Constant destination id for the local-notes home screen. */
const val HOME_DEST = "home"
const val AI_NOTES_DEST = "ai_notes"
const val ALARMS_DEST = "alarms"
const val AUDIOBOOKS_DEST = "audiobooks"
const val GALLERY_DEST = "gallery"
const val VIDEOS_DEST = "videos"
const val CHECKLISTS_DEST = "checklists"
const val CALENDAR_DEST = "calendar"
const val PRAYER_DEST = "prayer"
/** Not a rail page: the full-screen player, on the loaded (else last-played) file — the Player launcher icon. */
const val PLAYER_DEST = "player"
const val FLASHCARDS_DEST = "flashcards"
const val HIGHLIGHTS_DEST = "highlights"
const val COLLECT_DEST = "collect"
const val SEARCH_DEST = "search"
const val CONTACTS_DEST = "contacts"
const val SETTINGS_DEST = "settings"

/** Which band of the rail a destination belongs to. Settings belongs to neither — see [AppRail]. */
private enum class RailBand { LOCAL, SERVER }

/** The band a destination lives in, or null for one that sits outside both (Settings). */
private fun bandOf(dest: String): RailBand? = when (dest) {
    HOME_DEST, AI_NOTES_DEST, CALENDAR_DEST, ALARMS_DEST, AUDIOBOOKS_DEST, GALLERY_DEST, VIDEOS_DEST,
    PRAYER_DEST, COLLECT_DEST, CHECKLISTS_DEST -> RailBand.LOCAL
    SETTINGS_DEST -> null
    else -> RailBand.SERVER          // every record type, search, flash cards, highlights, contacts
}

/**
 * Persistent narrow navigation rail shown beside all browsing screens.
 *
 * Every destination but Settings lives in one of two bands: what works **on this phone** and what
 * comes **from the server**. That is the distinction that decides whether a page is worth opening
 * when the network is not there — the media player, the image gallery, the prayer times and the
 * file collector are all fully usable offline, while the record browsers show whatever the last
 * sync left behind.
 *
 * **One band is open at a time.** The rail carries eighteen destinations and cannot show them all
 * on a phone screen; an accordion means whichever half you are working in is fully visible without
 * scrolling, and the other is one tap away. It replaces the old "more" group, which folded four
 * pages behind a neutral icon for exactly the same reason and could only ever hide four of them.
 *
 * The band containing the current page opens itself: a collapsed band whose child is the current
 * page would hide where you already are. Tapping an open band's heading shuts it, so on a short
 * screen the local band can be folded away to bring the server band into view. When the rail is
 * still taller than the screen, an arrow at the bottom says so and scrolls to the rest. Settings belongs to neither band and is always visible at
 * the bottom, because a rail whose configuration page can be collapsed out of reach is a trap.
 */
@Composable
fun AppRail(
    selected: String,
    counts: Map<String, Int?>,
    /** Switched off in Settings, these two are not in the app at all — see SettingsStore. */
    prayerEnabled: Boolean = true,
    collectEnabled: Boolean = true,
    mediaEnabled: Boolean = true,
    galleryEnabled: Boolean = true,
    serverEnabled: Boolean = true,
    checklistsEnabled: Boolean = true,
    onChecklists: () -> Unit = {},
    onHome: () -> Unit,
    onAiNotes: () -> Unit,
    onAlarms: () -> Unit,
    onAudiobooks: () -> Unit,
    onGallery: () -> Unit,
    onVideos: () -> Unit,
    onCalendar: () -> Unit,
    onPrayer: () -> Unit,
    onFlashcards: () -> Unit,
    onHighlights: () -> Unit,
    onContacts: () -> Unit,
    /** Re-fetch every server-backed page — the band's own refresh. */
    onRefreshServer: () -> Unit,
    onCollect: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onType: (String) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(RailBand.LOCAL.name) }
    // Follows the selection rather than being set by it: opening a page from anywhere — a widget,
    // a notification, a link out of another page — should leave the rail showing where you are.
    LaunchedEffect(selected) { bandOf(selected)?.let { open = it.name } }
    // A band's heading toggles it; with both shut, only the two headings and Settings remain.
    val toggle = { band: RailBand -> open = if (open == band.name) NONE_OPEN else band.name }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.width(56.dp).fillMaxHeight()) {
    Column(
        modifier = Modifier
            .width(56.dp)
            .fillMaxHeight()
            .verticalScroll(scroll)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RailSection(
            icon = Icons.Filled.PhoneAndroid,
            label = stringResource(R.string.notes_rail_on_this_phone),
            tint = MaterialTheme.colorScheme.tertiary,
            alpha = LOCAL_ALPHA,
            // With no server band there is nothing to switch to, so this one is always open.
            expanded = open == RailBand.LOCAL.name || !serverEnabled,
            onToggle = { toggle(RailBand.LOCAL) },
        ) {
            // Order set deliberately: prayer times first, being the one opened at a fixed hour
            // rather than browsed, then notes, then the two media browsers, with the collector —
            // a job you run, not a place you read — last.
            if (prayerEnabled) {
                RailCell(selected == PRAYER_DEST, onPrayer) {
                    Icon(
                        Icons.Filled.Mosque,
                        contentDescription = stringResource(R.string.notes_rail_prayer_times),
                        tint = if (selected == PRAYER_DEST) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(RAIL_ICON_WIDE),
                    )
                }
            }
            // Beside the prayer times, because the two are the same kind of thing — the phone
            // waking you at a moment — and because a prayer-relative alarm is defined in terms of
            // what the page above it shows.
            RailCell(selected == ALARMS_DEST, onAlarms) {
                Icon(
                    Icons.Filled.Alarm,
                    contentDescription = stringResource(R.string.notes_rail_alarms),
                    tint = if (selected == ALARMS_DEST) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // A notes icon, not a house. This page is the local notes list, and inside a band
            // already labelled "on this phone" a house said nothing about what the page holds.
            RailCell(selected == HOME_DEST, onHome) {
                Icon(
                    Icons.AutoMirrored.Filled.StickyNote2,
                    contentDescription = stringResource(R.string.notes_rail_notes),
                    tint = if (selected == HOME_DEST) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Directly under the notes cell, because it is the same thing read a different way: a
            // folder of notes. Read-only, and the robot says so before the page does.
            RailCell(selected == AI_NOTES_DEST, onAiNotes) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = stringResource(R.string.notes_rail_ai_answers),
                    tint = if (selected == AI_NOTES_DEST) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // On this side of the rail because it works without the server: entries are saved on
            // the phone first and pushed by the outbox when a network appears.
            RailCell(selected == CALENDAR_DEST, onCalendar) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = stringResource(R.string.notes_rail_calendar),
                    tint = if (selected == CALENDAR_DEST) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Beside the calendar: the other page about what a day holds.
            if (checklistsEnabled) {
                RailCell(selected == CHECKLISTS_DEST, onChecklists) {
                    Icon(
                        Icons.Filled.Checklist,
                        contentDescription = stringResource(R.string.checklists_title),
                        tint = if (selected == CHECKLISTS_DEST) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (galleryEnabled) {
                RailCell(selected == GALLERY_DEST, onGallery) {
                    Icon(
                        Icons.Filled.PhotoLibrary,
                        contentDescription = stringResource(R.string.notes_rail_gallery),
                        tint = if (selected == GALLERY_DEST) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (mediaEnabled) {
                RailCell(selected == AUDIOBOOKS_DEST, onAudiobooks) {
                    Icon(
                        Icons.Filled.Headset,
                        contentDescription = stringResource(R.string.notes_rail_media),
                        tint = if (selected == AUDIOBOOKS_DEST) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Beside the audio browser: the same player plays both, this page just shows pictures.
                RailCell(selected == VIDEOS_DEST, onVideos) {
                    Icon(
                        Icons.Filled.VideoLibrary,
                        contentDescription = stringResource(R.string.notes_rail_videos),
                        tint = if (selected == VIDEOS_DEST) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Gathering files into the notes folder is a job done entirely on the phone; what it
            // collects is pushed later, by sync, which is a different thing from this page.
            if (collectEnabled) {
                RailCell(selected == COLLECT_DEST, onCollect) {
                    Icon(
                        Icons.Filled.DriveFolderUpload,
                        contentDescription = stringResource(R.string.notes_rail_collect_files),
                        tint = if (selected == COLLECT_DEST) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Switched off in Settings, the server is not in the app at all: no band, no pages.
        if (serverEnabled) {
            RailSection(
                icon = Icons.Filled.CloudQueue,
                label = stringResource(R.string.notes_rail_from_the_server),
                tint = MaterialTheme.colorScheme.primary,
                alpha = SERVER_ALPHA,
                expanded = open == RailBand.SERVER.name,
                onToggle = { toggle(RailBand.SERVER) },
            ) {
                NIBRAS_TYPES.forEach { type ->
                    val sel = type.code == selected
                    // Letter + count on one line, e.g. "T (12)", so the cell stays short.
                    RailCell(sel, { onType(type.code) }, height = 38.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                type.code,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            counts[type.code]?.let { c ->
                                Text(
                                    text = " ($c)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.width(36.dp))
                }
                RailCell(selected == FLASHCARDS_DEST, onFlashcards) {
                    Icon(
                        Icons.Filled.Style,
                        contentDescription = stringResource(R.string.notes_rail_flash_cards),
                        tint = if (selected == FLASHCARDS_DEST) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RailCell(selected == HIGHLIGHTS_DEST, onHighlights) {
                    Icon(
                        Icons.Filled.FormatQuote,
                        contentDescription = stringResource(R.string.notes_rail_highlights),
                        tint = if (selected == HIGHLIGHTS_DEST) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // The people this work is with — tasks are tagged by contact as well as by tag, and
                // this is the page that reads that back.
                RailCell(selected == CONTACTS_DEST, onContacts) {
                    Icon(
                        Icons.Filled.Groups,
                        contentDescription = stringResource(R.string.notes_rail_contacts),
                        tint = if (selected == CONTACTS_DEST) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Last in the band, and deliberately so: it searches the SERVER, over everything there,
                // where every cell above it browses what sync already pulled down. Ending the section
                // with the widest-reaching thing reads as "and if it is not up there, look here".
                RailCell(selected == SEARCH_DEST, onSearch) {
                    Icon(
                        Icons.Filled.TravelExplore,
                        contentDescription = stringResource(R.string.notes_rail_search_server),
                        tint = if (selected == SEARCH_DEST) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Closes the band: refresh what the band shows. The record browsers carry their own
                // refresh, but flash cards, highlights and contacts had none — the only way to pull new
                // rows was to visit a records page, refresh there, and come back.
                RailCell(false, onRefreshServer) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.notes_rail_refresh_server_pages),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Outside both bands, and never collapsed.
        HorizontalDivider(modifier = Modifier.width(36.dp))
        RailCell(selected == SETTINGS_DEST, onSettings) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = stringResource(R.string.notes_rail_settings),
                tint = if (selected == SETTINGS_DEST) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    // More rail below the screen's edge: without a cue, a short phone showed the local band and
    // nothing said the server band and Settings were under it. Tapping scrolls to the end.
    if (scroll.canScrollForward) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .clickable { scope.launch { scroll.animateScrollTo(scroll.maxValue) } }
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.notes_rail_more_below),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
    }
}

/** [AppRail]'s open band when both are shut. */
private const val NONE_OPEN = "none"

/**
 * One tinted band of the rail: a heading you can tap, and its destinations when it is open.
 *
 * The heading is an ICON, not a word. The rail is 56dp wide and "On this phone" at any legible size
 * would wrap to three lines or be cut; the words are still there for a screen reader, as the icon's
 * contentDescription, which is also what makes the band's purpose testable from the accessibility
 * tree rather than only by eye.
 *
 * [tint] is applied at a very low alpha. The band has to be visible as a grouping and invisible as
 * a control — a background competing with the selected cell's own fill makes the rail harder to
 * read, not easier. A collapsed band keeps the tint, so the two are still distinguishable when
 * shut, and gets a chevron so it is legible as something that opens.
 */
@Composable
private fun RailSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    alpha: Float,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp, vertical = 4.dp)
            .background(tint.copy(alpha = alpha), RoundedCornerShape(12.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The whole heading is the hit target, not just the icon: a 15dp glyph is not something to
        // aim at on a phone.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                // More below than above: the gap under the heading is what separates it from the
                // first destination, and without it the icon read as another cell in the list
                // rather than as the label over it.
                .padding(top = if (expanded) 5.dp else 9.dp, bottom = if (expanded) 9.dp else 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                icon,
                contentDescription = label,
                // One size in both states. An open band's heading drawn smaller than a shut one's
                // made the two look like different kinds of thing rather than the same control.
                modifier = Modifier.size(SECTION_ICON),
                tint = tint.copy(alpha = if (expanded) 0.8f else 1f),
            )
            if (!expanded) {
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = tint.copy(alpha = 0.6f),
                )
            }
        }
        if (expanded) content()
    }
}

/**
 * The two bands' fills.
 *
 * Different COLOURS were not enough on their own — tertiary and primary at the same faint alpha
 * read as one shade in most themes, which is the opposite of the point. The local band stays the
 * lighter of the two and the server band is noticeably deeper, so they are told apart by weight as
 * well as by hue, and still by the heading icon when neither reads clearly.
 */
private const val LOCAL_ALPHA = 0.07f
private const val SERVER_ALPHA = 0.20f

/**
 * Rail destination glyphs.
 *
 * [RAIL_ICON] is Material's own default, stated explicitly so a new cell inherits it rather than
 * whatever the next icon happens to draw. [RAIL_ICON_WIDE] is an OPTICAL correction, not a second
 * size: a few glyphs fill their 24dp box almost edge to edge while most leave a margin, so at the
 * same nominal size they read as bigger. Measured on a 720px-wide device, the mosque's ink was 45px
 * across against 33-41 for its neighbours; 20dp brings it into the same range.
 *
 * Applied per glyph and never by default — shrinking every icon to suit one of them would make the
 * rest small.
 */
internal val RAIL_ICON = 24.dp
internal val RAIL_ICON_WIDE = 20.dp

/** Heading glyph size, shared by both bands and both states so they read as one control. */
private val SECTION_ICON = 20.dp

@Composable
private fun RailCell(
    selected: Boolean,
    onClick: () -> Unit,
    height: Dp = 56.dp,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}
