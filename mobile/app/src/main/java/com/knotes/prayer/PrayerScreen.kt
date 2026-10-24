package com.knotes.prayer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import android.media.RingtoneManager
import androidx.compose.material3.Switch
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.knotes.R
import com.knotes.UiStateStore
import com.knotes.ui.StepSlider
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The settings groups the page can open under its list of times. */
private enum class PrayerPanel { TIMES, METHOD, SOUND, SILENCE, HIJRI }

/**
 * Today's prayer times, with every knob that shapes them on the same page.
 *
 * The settings live here rather than in the app's Settings screen for the same reason the player's
 * do: they are only meaningful next to their effect. Nudging Asr by three minutes is a decision you
 * make while looking at what Asr currently says, and picking a calculation method is guesswork
 * until you can see the six times move.
 *
 * Every control writes through immediately — there is no Save button — and each write re-arms the
 * alarm chain, so what is on screen and what will actually go off never drift apart.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrayerScreen(store: PrayerSettingsStore) {
    val context = LocalContext.current
    val settings by store.state.collectAsState()
    val zone = remember { ZoneId.systemDefault() }

    // Ticks once a second, for the countdown.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val minute = now / 60_000

    fun update(block: (PrayerSettings) -> PrayerSettings) {
        store.save(block(store.value))
        PrayerScheduler.sync(context)
    }

    val date = remember(minute) { LocalDate.ofInstant(Instant.ofEpochMilli(now), zone) }
    val day = remember(settings, date) { PrayerCalendar.dayOf(settings, date, zone) }
    // Recomputed each minute — and since every prayer time is rounded to a whole minute, the tick
    // that retires one is exactly the tick that recomputes this. The countdown never goes negative.
    val upcoming = remember(settings, minute) { PrayerCalendar.nextPrayer(settings, zone, now) }
    // The time that has just arrived, if any — the header counts up from it for a while.
    val recent = remember(settings, minute) { PrayerCalendar.recentPrayer(settings, zone, now) }
    val hijri = remember(settings, minute) { PrayerCalendar.hijriNow(settings, zone, now) }

    var panel by remember {
        mutableStateOf(panelFromName(UiStateStore.prayerPanel(context)))
    }
    LaunchedEffect(panel) { UiStateStore.setPrayerPanel(context, panel?.name) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Header(settings, hijri, date, upcoming, recent, now)

        Spacer(Modifier.height(12.dp))
        day.visibleSlots(settings).forEach { slot ->
            PrayerRow(
                slot = slot,
                isNext = if (recent != null) recent.prayer == slot.prayer
                else upcoming?.prayer == slot.prayer && upcoming.isTomorrow.not(),
                hasAthan = slot.prayer in settings.effectiveAthan,
            )
        }
        if (settings.visible.isEmpty()) {
            Text(
                stringResource(R.string.prayer_none_shown),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        }

        Spacer(Modifier.height(14.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PanelChip(Icons.Filled.VisibilityOff, stringResource(R.string.prayer_panel_times), panel == PrayerPanel.TIMES) {
                panel = if (panel == PrayerPanel.TIMES) null else PrayerPanel.TIMES
            }
            PanelChip(Icons.Filled.Tune, stringResource(R.string.prayer_panel_method), panel == PrayerPanel.METHOD) {
                panel = if (panel == PrayerPanel.METHOD) null else PrayerPanel.METHOD
            }
            PanelChip(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.prayer_panel_athan), panel == PrayerPanel.SOUND) {
                panel = if (panel == PrayerPanel.SOUND) null else PrayerPanel.SOUND
            }
            PanelChip(Icons.Filled.NotificationsOff, stringResource(R.string.prayer_panel_silence), panel == PrayerPanel.SILENCE) {
                panel = if (panel == PrayerPanel.SILENCE) null else PrayerPanel.SILENCE
            }
            PanelChip(Icons.Filled.CalendarMonth, stringResource(R.string.prayer_panel_hijri), panel == PrayerPanel.HIJRI) {
                panel = if (panel == PrayerPanel.HIJRI) null else PrayerPanel.HIJRI
            }
        }

        Spacer(Modifier.height(10.dp))
        when (panel) {
            PrayerPanel.TIMES -> TimesPanel(settings, day, ::update)
            PrayerPanel.METHOD -> MethodPanel(settings, ::update)
            PrayerPanel.SOUND -> SoundPanel(settings, ::update)
            PrayerPanel.SILENCE -> SilencePanel(settings, ::update)
            PrayerPanel.HIJRI -> HijriPanel(settings, hijri, ::update)
            null -> {}
        }

        Spacer(Modifier.height(28.dp))
    }
}

// ---- header ------------------------------------------------------------------------------------

@Composable
private fun Header(
    settings: PrayerSettings,
    hijri: HijriDate,
    date: LocalDate,
    upcoming: UpcomingPrayer?,
    recent: UpcomingPrayer?,
    now: Long,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            hijri.label(context),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.getDefault())) +
                settings.placeName.trim().takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (recent != null) {
                // Just after a time: how long ago it was, ticking up, with the next one beneath.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            withArabic(recent.prayer),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            stringResource(R.string.prayer_since_duration, elapsedClock(now - recent.atMillis)),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (upcoming != null) {
                            Text(
                                withArabic(upcoming.prayer) + " " +
                                    stringResource(R.string.prayer_in_duration, countdown(context, upcoming.atMillis - now)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Text(
                        clock(recent.atMillis),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            } else if (upcoming == null) {
                Text(
                    stringResource(R.string.prayer_no_upcoming),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            withArabic(upcoming.prayer) +
                                if (upcoming.isTomorrow) "  " + stringResource(R.string.prayer_tomorrow_paren) else "",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            stringResource(R.string.prayer_in_duration, countdown(context, upcoming.atMillis - now)),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Text(
                        clock(upcoming.atMillis),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        if (!settings.enabled) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.prayer_alarms_off_warning),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun PrayerRow(slot: PrayerSlot, isNext: Boolean, hasAthan: Boolean) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(
                if (isNext) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                shape,
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val name = stringResource(slot.prayer.labelRes)
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(86.dp),
        )
        // The Arabic name beside the translated one — blank when the UI is Arabic and it would repeat.
        Text(
            if (name == slot.prayer.arabic) "" else slot.prayer.arabic,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (hasAthan) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = stringResource(R.string.prayer_athan_on_cd),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        // The nudge is shown beside the time it produced, so a row that disagrees with the mosque
        // by five minutes never looks like the calculation being wrong.
        if (slot.adjustmentMin != 0) {
            Text(
                "%+d".format(slot.adjustmentMin),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            slot.atMillis?.let(::clock) ?: "—",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ---- panels ------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimesPanel(
    settings: PrayerSettings,
    day: PrayerDay,
    update: ((PrayerSettings) -> PrayerSettings) -> Unit,
) {
    PanelBody(stringResource(R.string.prayer_which_times)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Prayer.entries.forEach { p ->
                FilterChip(
                    selected = p in settings.visible,
                    onClick = {
                        update { s ->
                            s.copy(
                                visible = if (p in s.visible) s.visible - p else s.visible + p,
                            )
                        }
                    },
                    label = { Text(stringResource(p.labelRes)) },
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.prayer_adjust_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Note(stringResource(R.string.prayer_adjust_note))
        Spacer(Modifier.height(4.dp))
        Prayer.entries.forEach { p ->
            val slot = day.slot(p)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(p.labelRes), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(84.dp))
                IconButton(
                    onClick = { update { s -> s.withAdjustment(p, s.adjustmentOf(p) - 1) } },
                    enabled = settings.adjustmentOf(p) > PrayerSettings.MIN_ADJUST,
                    modifier = Modifier.size(34.dp),
                ) { Icon(Icons.Filled.Remove, stringResource(R.string.prayer_earlier_cd, stringResource(p.labelRes)), modifier = Modifier.size(18.dp)) }
                Text(
                    "%+d".format(settings.adjustmentOf(p)),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(40.dp),
                )
                IconButton(
                    onClick = { update { s -> s.withAdjustment(p, s.adjustmentOf(p) + 1) } },
                    enabled = settings.adjustmentOf(p) < PrayerSettings.MAX_ADJUST,
                    modifier = Modifier.size(34.dp),
                ) { Icon(Icons.Filled.Add, stringResource(R.string.prayer_later_cd, stringResource(p.labelRes)), modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(8.dp))
                Text(
                    slot?.atMillis?.let(::clock) ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (settings.adjustmentOf(p) != 0) {
                    Text(
                        stringResource(R.string.prayer_reset),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { update { s -> s.withAdjustment(p, 0) } },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MethodPanel(settings: PrayerSettings, update: ((PrayerSettings) -> PrayerSettings) -> Unit) {
    PanelBody(stringResource(R.string.prayer_school_title)) {
        Note(stringResource(R.string.prayer_school_note))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AsrSchool.entries.forEach { school ->
                FilterChip(
                    selected = settings.asr == school,
                    onClick = { update { it.copy(asr = school) } },
                    label = { Text(stringResource(school.labelRes)) },
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.prayer_method_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Note(stringResource(R.string.prayer_method_note))
        CalcMethod.entries.forEach { method ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { update { it.copy(method = method) } }
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = settings.method == method,
                    onCheckedChange = { update { s -> s.copy(method = method) } },
                )
                Column {
                    Text(stringResource(method.labelRes), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        listOfNotNull(
                            stringResource(R.string.prayer_method_fajr_angle, trimAngle(method.fajrAngle)),
                            method.ishaAngle?.let { stringResource(R.string.prayer_method_isha_angle, trimAngle(it)) }
                                ?: stringResource(R.string.prayer_method_isha_minutes, method.ishaMinutes),
                            method.maghribAngle?.let {
                                stringResource(R.string.prayer_method_maghrib_angle, trimAngle(it))
                            },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.prayer_location_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        LocationFields(settings, update)

        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.prayer_highlat_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Note(stringResource(R.string.prayer_highlat_note))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HighLatRule.entries.forEach { rule ->
                FilterChip(
                    selected = settings.highLat == rule,
                    onClick = { update { it.copy(highLat = rule) } },
                    label = { Text(stringResource(rule.labelRes)) },
                )
            }
        }
    }
}

@Composable
private fun LocationFields(
    settings: PrayerSettings,
    update: ((PrayerSettings) -> PrayerSettings) -> Unit,
) {
    val context = LocalContext.current

    /*
     * Local text state, deliberately NOT keyed on the setting it edits.
     *
     * Keying it there is the obvious thing and it makes the field unusable: every keystroke that
     * parses commits, committing changes the setting, and a changed key rebuilds the state from
     * storage — so typing "33.9" would have the field rewrite itself underneath the cursor. The
     * text is the user's to hold until they leave; only the button below reaches in to set it.
     */
    var lat by remember { mutableStateOf(formatCoord(settings.latitude)) }
    var lon by remember { mutableStateOf(formatCoord(settings.longitude)) }
    var place by remember { mutableStateOf(settings.placeName) }

    fun applyLocation(la: Double, lo: Double) {
        lat = formatCoord(la)
        lon = formatCoord(lo)
        update { it.copy(latitude = la, longitude = lo) }
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) lastKnownLocation(context)?.let { (la, lo) -> applyLocation(la, lo) }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = lat,
            // A half-typed "-" or "33." simply does not commit; the last good value stays in force.
            onValueChange = { text ->
                lat = text
                text.toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
                    ?.let { v -> update { s -> s.copy(latitude = v) } }
            },
            label = { Text(stringResource(R.string.prayer_latitude)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = lon,
            onValueChange = { text ->
                lon = text
                text.toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
                    ?.let { v -> update { s -> s.copy(longitude = v) } }
            },
            label = { Text(stringResource(R.string.prayer_longitude)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = place,
        onValueChange = { place = it; update { s -> s.copy(placeName = it) } },
        label = { Text(stringResource(R.string.prayer_place_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    Button(onClick = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            lastKnownLocation(context)?.let { (la, lo) -> applyLocation(la, lo) }
        } else {
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }) {
        Icon(Icons.Filled.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.prayer_use_location))
    }
    Note(stringResource(R.string.prayer_location_note))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundPanel(settings: PrayerSettings, update: ((PrayerSettings) -> PrayerSettings) -> Unit) {
    val context = LocalContext.current
    /*
     * The athan is picked from the phone's FILES, not from the ringtone list.
     *
     * The ringtone picker offers notification tones and alarm jingles — the system's own sounds.
     * An athan recording is a file someone downloaded, and it is never in there, so the picker was
     * offering a list that could not contain the answer. A persistable read grant is taken because
     * the alarm receiver has to open this URI months later, after reboots, with the app closed.
     */
    val soundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            update { it.copy(athanSoundUri = uri.toString()) }
        }
    }

    // Beeps can come from the ringtone list OR from a file. Unlike the athan — which is a specific
    // recording nobody's ringtone list contains — a countdown mark is exactly the sort of short
    // sound the system already ships, so offering both is worth the second button.
    val beepFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            update { it.copy(beepSoundUri = uri.toString()) }
        }
    }
    val beepRingtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        // A null pick is "Silent" or a cancel; neither should wipe a sound already chosen.
        if (uri != null) update { it.copy(beepSoundUri = uri.toString()) }
    }

    // Read here: the picker intent is built in a click handler, outside composition.
    val beepSoundTitle = stringResource(R.string.prayer_beep_sound)
    PanelBody(stringResource(R.string.prayer_panel_athan)) {
        ToggleRow(
            stringResource(R.string.prayer_alarms_on),
            stringResource(R.string.prayer_alarms_on_note),
            settings.enabled,
        ) { on -> update { it.copy(enabled = on) } }

        /*
         * Whether the feature is in the app at all — moved here from the global Settings screen, so
         * that everything about prayer times is configured on the prayer page.
         *
         * Note what it does that "Alarms on" above does not: it takes the page out of the rail as
         * well as cancelling the schedule. Which is why turning it off from HERE is a one-way door
         * — the page you are standing on is the page that disappears — and why Settings keeps a
         * single "Prayer times are hidden · Show" row while it is off. That row is the way back in;
         * without it the switch could not be undone.
         */
        Spacer(Modifier.height(8.dp))
        val appSettings = remember { com.knotes.SettingsStore(context) }
        val appState by appSettings.state.collectAsState()
        ToggleRow(
            stringResource(R.string.prayer_in_app),
            stringResource(R.string.prayer_in_app_note),
            appState.prayerEnabled,
        ) { on -> appSettings.save(appState.copy(prayerEnabled = on)) }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.prayer_call_athan_at), style = MaterialTheme.typography.bodyMedium)
        Note(stringResource(R.string.prayer_shurooq_note))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Prayer.entries.filter { it.isPrayer }.forEach { p ->
                FilterChip(
                    selected = p in settings.effectiveAthan,
                    onClick = {
                        update { s ->
                            val set = s.effectiveAthan
                            s.copy(athanPrayers = if (p in set) set - p else set + p)
                        }
                    },
                    label = { Text(stringResource(p.labelRes)) },
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { soundPicker.launch(arrayOf("audio/*")) }) { Text(stringResource(R.string.prayer_choose_athan_file)) }
            // Plays the chosen file on the alarm stream at the phone's own alarm volume — "does
            // this file play at all". The per-prayer buttons below answer "how loud": they set the
            // alarm volume exactly as the athan will.
            Button(onClick = {
                PrayerReceiver.soundAthanAt(context, settings, PrayerSettings.MAX_VOLUME, setDeviceLevel = false)
            }) { Text(stringResource(R.string.prayer_hear_it)) }
            TextButton(onClick = { PrayerReceiver.stopSound() }) { Text(stringResource(R.string.prayer_stop)) }
            if (settings.athanSoundUri.isNotBlank()) {
                TextButton(onClick = { update { it.copy(athanSoundUri = "") } }) { Text(stringResource(R.string.prayer_clear)) }
            }
        }
        Text(
            soundLabel(context, settings.athanSoundUri),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Note(stringResource(R.string.prayer_athan_file_note))

        // Per-prayer volume: the Fajr athan and the Dhuhr athan want different volumes from the
        // same phone, and one setting cannot serve both.
        if (settings.effectiveAthan.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.prayer_volume_each),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Note(stringResource(R.string.prayer_volume_note))
            Prayer.entries.filter { it in settings.effectiveAthan }.forEach { p ->
                val v = settings.athanVolumeOf(p)
                StepSlider(
                    if (v == 0) stringResource(R.string.prayer_volume_muted, stringResource(p.labelRes))
                    else stringResource(R.string.prayer_volume_percent, stringResource(p.labelRes), v),
                    v.toFloat(),
                    PrayerSettings.MIN_VOLUME.toFloat(),
                    PrayerSettings.MAX_VOLUME.toFloat(),
                    5f,
                    labelWidth = 132.dp,
                ) { nv -> update { s -> s.withAthanVolume(p, nv.toInt()) } }
            }
            // A percentage does not tell you how loud a room will be at 4am. These play the athan
            // at exactly the volume that prayer would use, so the number can be judged by ear.
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.prayer_hear_colon),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                Prayer.entries.filter { it in settings.effectiveAthan }.forEach { p ->
                    TextButton(onClick = { PrayerReceiver.soundAthan(context, settings, p) }) {
                        Text(stringResource(p.labelRes))
                    }
                }
                TextButton(onClick = { PrayerReceiver.stopSound() }) { Text(stringResource(R.string.prayer_stop)) }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))
        // The widget's countdown, not an alarm — so it sits above the reminder rather than inside it.
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.prayer_widget_countdown),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Note(stringResource(R.string.prayer_widget_countdown_note))
        StepSlider(
            countdownLabel(context, settings.countdownWithinMin),
            settings.countdownWithinMin.toFloat(),
            PrayerSettings.MIN_COUNTDOWN.toFloat(),
            PrayerSettings.MAX_COUNTDOWN.toFloat(),
            15f,
            labelWidth = 138.dp,
        ) { v -> update { it.copy(countdownWithinMin = v.toInt()) } }
        Spacer(Modifier.height(8.dp))
        Note(stringResource(R.string.prayer_elapsed_note))
        StepSlider(
            if (settings.elapsedForMin <= 0) stringResource(R.string.prayer_elapsed_off)
            else stringResource(R.string.prayer_elapsed_for, settings.elapsedForMin),
            settings.elapsedForMin.toFloat(),
            PrayerSettings.MIN_ELAPSED.toFloat(),
            PrayerSettings.MAX_ELAPSED.toFloat(),
            5f,
            labelWidth = 138.dp,
        ) { v -> update { it.copy(elapsedForMin = v.toInt()) } }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))
        ToggleRow(
            stringResource(R.string.prayer_remind_before),
            stringResource(R.string.prayer_remind_before_note),
            settings.reminderEnabled,
        ) { on -> update { it.copy(reminderEnabled = on) } }
        if (settings.reminderEnabled) {
            StepSlider(
                stringResource(R.string.prayer_min_before, settings.reminderLeadMin),
                settings.reminderLeadMin.toFloat(),
                PrayerSettings.MIN_LEAD.toFloat(),
                PrayerSettings.MAX_LEAD.toFloat(),
                1f,
            ) { v -> update { it.copy(reminderLeadMin = v.toInt()) } }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))
        ToggleRow(
            stringResource(R.string.prayer_beeps_before),
            stringResource(R.string.prayer_beeps_note),
            settings.beepsEnabled,
        ) { on -> update { it.copy(beepsEnabled = on) } }
        if (settings.beepsEnabled) {
            StepSlider(
                stringResource(R.string.prayer_min_before, settings.beepsLeadMin),
                settings.beepsLeadMin.toFloat(),
                PrayerSettings.MIN_LEAD.toFloat(),
                PrayerSettings.MAX_LEAD.toFloat(),
                1f,
            ) { v -> update { it.copy(beepsLeadMin = v.toInt()) } }
            StepSlider(
                pluralStringResource(R.plurals.prayer_beeps_count, settings.beepCount, settings.beepCount),
                settings.beepCount.toFloat(),
                PrayerSettings.MIN_BEEPS.toFloat(),
                PrayerSettings.MAX_BEEPS.toFloat(),
                1f,
            ) { v -> update { it.copy(beepCount = v.toInt()) } }

            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.prayer_beep_sound), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, beepSoundTitle)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        if (settings.beepSoundUri.isNotBlank()) {
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                Uri.parse(settings.beepSoundUri),
                            )
                        }
                    }
                    beepRingtonePicker.launch(intent)
                }) { Text(stringResource(R.string.prayer_ringtones)) }
                Button(onClick = { beepFilePicker.launch(arrayOf("audio/*")) }) { Text(stringResource(R.string.prayer_a_file)) }
                if (settings.beepSoundUri.isNotBlank()) {
                    TextButton(onClick = { update { it.copy(beepSoundUri = "") } }) {
                        Text(stringResource(R.string.prayer_builtin_tone))
                    }
                }
            }
            Text(
                if (settings.beepSoundUri.isBlank()) stringResource(R.string.prayer_builtin_tone_no_file)
                else soundLabel(context, settings.beepSoundUri),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // The whole sequence, not one play: a ringtone picker's own preview plays the file
                // once at media volume, where the alarm plays it beepCount times on the alarm
                // stream. The count and the stream are exactly the parts that can surprise you.
                Button(onClick = { PrayerReceiver.soundBeeps(context, settings) }) {
                    Text(stringResource(R.string.prayer_hear_count, settings.beepCount))
                }
                TextButton(onClick = { PrayerReceiver.stopSound() }) { Text(stringResource(R.string.prayer_stop)) }
            }
            Note(stringResource(R.string.prayer_beep_tone_note))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SilencePanel(settings: PrayerSettings, update: ((PrayerSettings) -> PrayerSettings) -> Unit) {
    val context = LocalContext.current
    // Re-read on every recomposition rather than remembering: the user leaves the app to grant this
    // and comes straight back, and a remembered "false" would still be claiming it is missing.
    val granted = SilenceState.canControlRinger(context)

    PanelBody(stringResource(R.string.prayer_silence_title)) {
        // None / Silence / Vibration — one choice, stored as the on/off switch plus the mode, so
        // the schedule (which only asks "is anything to be done?") stays as it was.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                Triple(R.string.prayer_after_none, false, settings.silenceVibrate),
                Triple(R.string.prayer_after_silence, true, false),
                Triple(R.string.prayer_after_vibrate, true, true),
            ).forEach { (label, on, vibrate) ->
                FilterChip(
                    selected = settings.silenceEnabled == on && (!on || settings.silenceVibrate == vibrate),
                    onClick = { update { it.copy(silenceEnabled = on, silenceVibrate = vibrate) } },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        Note(stringResource(R.string.prayer_silence_toggle_note))

        if (settings.silenceEnabled && !granted) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.prayer_dnd_needed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }) { Text(stringResource(R.string.prayer_grant_access)) }
        }

        if (settings.silenceEnabled) {
            Spacer(Modifier.height(10.dp))
            StepSlider(
                stringResource(R.string.prayer_silence_for, settings.silenceMinutes),
                settings.silenceMinutes.toFloat(),
                PrayerSettings.MIN_SILENCE.toFloat(),
                PrayerSettings.MAX_SILENCE.toFloat(),
                5f,
            ) { v -> update { it.copy(silenceMinutes = v.toInt()) } }

            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.prayer_at_these_times), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Prayer.entries.filter { it.isPrayer }.forEach { p ->
                    FilterChip(
                        selected = p in settings.effectiveSilence,
                        onClick = {
                            update { s ->
                                val set = s.effectiveSilence
                                s.copy(silencePrayers = if (p in set) set - p else set + p)
                            }
                        },
                        label = { Text(stringResource(p.labelRes)) },
                    )
                }
            }
            Note(stringResource(R.string.prayer_silence_note))
        }
    }
}

@Composable
private fun HijriPanel(
    settings: PrayerSettings,
    hijri: HijriDate,
    update: ((PrayerSettings) -> PrayerSettings) -> Unit,
) {
    val context = LocalContext.current
    PanelBody(stringResource(R.string.prayer_islamic_date)) {
        val dateText = hijri.label(context)
        Text(
            // The Arabic month rides along, unless the date is already written in Arabic.
            if (hijri.monthName(context) == hijri.monthArabic) dateText else "$dateText  ·  ${hijri.monthArabic}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Note(stringResource(R.string.prayer_hijri_note))
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.prayer_shift_by), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(80.dp))
            IconButton(
                onClick = { update { it.copy(hijriOffsetDays = it.hijriOffsetDays - 1) } },
                enabled = settings.hijriOffsetDays > Hijri.MIN_OFFSET,
                modifier = Modifier.size(36.dp),
            ) { Icon(Icons.Filled.Remove, stringResource(R.string.prayer_day_earlier_cd), modifier = Modifier.size(18.dp)) }
            Text(
                pluralStringResource(
                    R.plurals.prayer_shift_days,
                    kotlin.math.abs(settings.hijriOffsetDays),
                    settings.hijriOffsetDays,
                ),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(72.dp),
            )
            IconButton(
                onClick = { update { it.copy(hijriOffsetDays = it.hijriOffsetDays + 1) } },
                enabled = settings.hijriOffsetDays < Hijri.MAX_OFFSET,
                modifier = Modifier.size(36.dp),
            ) { Icon(Icons.Filled.Add, stringResource(R.string.prayer_day_later_cd), modifier = Modifier.size(18.dp)) }
        }

        Spacer(Modifier.height(10.dp))
        ToggleRow(
            stringResource(R.string.prayer_day_starts_maghrib),
            stringResource(R.string.prayer_day_starts_maghrib_note),
            settings.hijriDayStartsAtMaghrib,
        ) { on -> update { it.copy(hijriDayStartsAtMaghrib = on) } }
    }
}

// ---- small pieces ------------------------------------------------------------------------------

@Composable
private fun PanelBody(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(10.dp),
            )
            .padding(12.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 3.dp),
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PanelChip(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
    )
}

// ---- helpers -----------------------------------------------------------------------------------

private fun PrayerSettings.withAthanVolume(prayer: Prayer, percent: Int): PrayerSettings =
    copy(
        athanVolumes = athanVolumes.toMutableMap().apply {
            // A 0 is stored, not dropped: "mute this one" is a setting, and removing the entry
            // would silently restore the default instead.
            this[prayer] = percent.coerceIn(PrayerSettings.MIN_VOLUME, PrayerSettings.MAX_VOLUME)
        },
    )

private fun PrayerSettings.withAdjustment(prayer: Prayer, minutes: Int): PrayerSettings {
    val clamped = minutes.coerceIn(PrayerSettings.MIN_ADJUST, PrayerSettings.MAX_ADJUST)
    val next = adjustments.toMutableMap()
    if (clamped == 0) next.remove(prayer) else next[prayer] = clamped
    return copy(adjustments = next)
}

private fun clock(millis: Long): String =
    java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(millis))

/** "2h 14m" / "14m 03s" — seconds only once it is close enough for them to matter. */
/**
 * "12:34" or "1:02:03" for the elapsed timer, fenced in left-to-right marks so bidi keeps it in
 * clock order beside the Arabic "منذ" — the same treatment the widget gives its Chronometer.
 */
private fun elapsedClock(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val text = if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
    return "\u200E$text\u200E"
}

private fun countdown(context: Context, deltaMillis: Long): String {
    val total = (deltaMillis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return when {
        h > 0 -> context.getString(R.string.prayer_dur_hours_minutes, h, m)
        m > 0 -> context.getString(R.string.prayer_dur_minutes_seconds, m, s)
        else -> context.getString(R.string.prayer_dur_seconds, s)
    }
}

/**
 * A coordinate as a human would write it — six decimals is about 10 cm, and anything beyond that is
 * the binary representation showing through rather than information.
 */
internal fun formatCoord(value: Double): String {
    val rounded = Math.round(value * 1_000_000.0) / 1_000_000.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
    else rounded.toString()
}

/** Trims "17.0°" to "17°" while leaving "17.7°" alone. */
private fun trimAngle(angle: Double): String =
    if (angle == angle.toInt().toDouble()) angle.toInt().toString() else angle.toString()

/** The picked file's display name, so the row says which athan rather than a content URI. */
private fun soundLabel(
    context: Context,
    uri: String,
    whenBlank: String = context.getString(R.string.prayer_no_file_default),
): String {
    if (uri.isBlank()) return whenBlank
    val parsed = runCatching { Uri.parse(uri) }.getOrNull()
        ?: return context.getString(R.string.prayer_chosen_file)
    val name = runCatching {
        context.contentResolver.query(parsed, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else null
        }
    }.getOrNull()
    return name ?: parsed.lastPathSegment?.substringAfterLast('/')
        ?: context.getString(R.string.prayer_chosen_file)
}

/**
 * The phone's last known coarse position, or null.
 *
 * Deliberately *last known* rather than a live fix: a prayer time moves by seconds across a whole
 * town, so there is nothing to gain from waking the GPS and waiting, and a cached position costs
 * the user no battery and no time.
 */
private fun lastKnownLocation(context: Context): Pair<Double, Double>? = runCatching {
    val lm = context.getSystemService(LocationManager::class.java) ?: return null
    listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
        .asSequence()
        .mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
        .firstOrNull()
        ?.let { it.latitude to it.longitude }
}.getOrNull()

/** Reads back a stored panel name; "never chosen" opens nothing, so the times lead. */
private fun panelFromName(name: String?): PrayerPanel? = when (name) {
    UiStateStore.ABSENT -> null
    null -> null
    else -> PrayerPanel.entries.firstOrNull { it.name == name }
}

/**
 * "45 min before" / "2h before", and "always showing" at the top of the range.
 *
 * The maximum is longer than the gap between any two prayers, so at that setting the countdown is
 * never hidden — saying so is clearer than making someone work out what 720 minutes buys them.
 */
private fun countdownLabel(context: Context, minutes: Int): String = when {
    minutes >= PrayerSettings.MAX_COUNTDOWN -> context.getString(R.string.prayer_countdown_always)
    minutes % 60 == 0 -> context.getString(R.string.prayer_hours_before, minutes / 60)
    minutes > 60 -> context.getString(R.string.prayer_hours_minutes_before, minutes / 60, minutes % 60)
    else -> context.getString(R.string.prayer_min_before, minutes)
}

/** "Fajr · الفجر" — just "الفجر" when the UI is already Arabic. */
@Composable
private fun withArabic(prayer: Prayer): String {
    val name = stringResource(prayer.labelRes)
    return if (name == prayer.arabic) name else "$name · ${prayer.arabic}"
}
