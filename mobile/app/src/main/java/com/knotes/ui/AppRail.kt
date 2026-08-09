package com.knotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.knotes.nibras.NIBRAS_TYPES

/** Constant destination id for the local-notes home screen. */
const val HOME_DEST = "home"
const val AUDIOBOOKS_DEST = "audiobooks"
const val GALLERY_DEST = "gallery"
const val CALENDAR_DEST = "calendar"
const val COLLECT_DEST = "collect"
const val SEARCH_DEST = "search"
const val SETTINGS_DEST = "settings"

/**
 * Persistent narrow navigation rail shown beside all browsing screens. A Home button at the
 * top returns to the local-notes screen; below it, one letter per Nibras type (with a count
 * badge) opens that type.
 */
@Composable
fun AppRail(
    selected: String,
    counts: Map<String, Int?>,
    onHome: () -> Unit,
    onAudiobooks: () -> Unit,
    onGallery: () -> Unit,
    onCalendar: () -> Unit,
    onCollect: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onType: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(56.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RailCell(selected == HOME_DEST, onHome) {
            Icon(
                Icons.Filled.Home,
                contentDescription = "Home",
                tint = if (selected == HOME_DEST) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RailCell(selected == AUDIOBOOKS_DEST, onAudiobooks) {
            Icon(
                Icons.Filled.Headset,
                contentDescription = "Audiobooks",
                tint = if (selected == AUDIOBOOKS_DEST) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RailCell(selected == GALLERY_DEST, onGallery) {
            Icon(
                Icons.Filled.PhotoLibrary,
                contentDescription = "Gallery",
                tint = if (selected == GALLERY_DEST) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Searching the SERVER, distinct from the per-type browsers below, which only ever show
        // what sync already pulled down.
        RailCell(selected == SEARCH_DEST, onSearch) {
            Icon(
                Icons.Filled.TravelExplore,
                contentDescription = "Search server",
                tint = if (selected == SEARCH_DEST) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RailCell(selected == CALENDAR_DEST, onCalendar) {
            Icon(
                Icons.Filled.CalendarMonth,
                contentDescription = "Calendar",
                tint = if (selected == CALENDAR_DEST) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(modifier = Modifier.width(36.dp))
        NIBRAS_TYPES.forEachIndexed { index, type ->
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
            // Separator line between navigation items.
            if (index < NIBRAS_TYPES.lastIndex) {
                HorizontalDivider(modifier = Modifier.width(36.dp))
            }
        }
        // Tools at the bottom of the rail: collect files + settings (their own pages now).
        HorizontalDivider(modifier = Modifier.width(36.dp))
        RailCell(selected == COLLECT_DEST, onCollect) {
            Icon(
                Icons.Filled.DriveFolderUpload,
                contentDescription = "Collect files",
                tint = if (selected == COLLECT_DEST) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RailCell(selected == SETTINGS_DEST, onSettings) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = if (selected == SETTINGS_DEST) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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
