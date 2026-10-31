package com.knotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knotes.R
import com.knotes.alarm.Alarm
import com.knotes.alarm.AlarmKind
import com.knotes.alarm.AlarmTimes
import com.knotes.alarm.minutesLabel
import com.knotes.alarm.offsetLabel
import com.knotes.prayer.Prayer
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * The alarms page: a clock alarm, a countdown and a prayer-relative alarm in one list.
 *
 * They share a list because they share everything that happens when one fires — the sound, how many
 * times it plays, how loud. Only *when* differs, and that is one field on the form.
 *
 * Every row shows the time it will actually next ring, computed the same way the scheduler computes
 * it. A list that says "07:00, Mondays" without saying "in 3 days" is the list that lets a wrong
 * alarm sit there looking right.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmsScreen(
    alarms: List<Alarm>,
    exactAllowed: Boolean,
    prayerTimeAt: (LocalDate, Prayer) -> Long?,
    onSave: (Alarm) -> Unit,
    onDelete: (String) -> Unit,
    onPickSound: ((String) -> Unit) -> Unit,
) {
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var confirmDelete by remember { mutableStateOf<Alarm?>(null) }
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Alarm(id = com.knotes.alarm.AlarmStore.newId()) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.prayer_alarm_add_cd))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.prayer_alarms_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.prayer_alarms_count_on, alarms.count { it.enabled }),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!exactAllowed) {
                // Said once, plainly. Without the permission Android delivers these late, and an
                // alarm quietly firing ten minutes after its time is worse than one that says why.
                Text(
                    stringResource(R.string.prayer_alarm_exact_warning),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            if (alarms.isEmpty()) {
                Text(
                    stringResource(R.string.prayer_alarm_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                ) {
                    items(alarms, key = { it.id }) { alarm ->
                        AlarmRow(
                            alarm = alarm,
                            nextAt = AlarmTimes.nextFire(alarm, now, zone, prayerTimeAt),
                            zone = zone,
                            onToggle = { onSave(alarm.copy(enabled = it)) },
                            onEdit = { editing = alarm },
                            // A copy opens in the editor first, so it is changed (a new time, another
                            // sound) before it exists; Cancel leaves no second alarm behind. A running
                            // countdown's copy starts stopped.
                            onDuplicate = { editing = alarm.copy(id = com.knotes.alarm.AlarmStore.newId(), dueAtMillis = null) },
                            onDelete = { confirmDelete = alarm },
                            onStartStop = {
                                onSave(
                                    if (alarm.dueAtMillis == null) {
                                        alarm.copy(
                                            dueAtMillis = System.currentTimeMillis() +
                                                alarm.durationMin * 60_000L,
                                            enabled = true,
                                        )
                                    } else {
                                        alarm.copy(dueAtMillis = null)
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    editing?.let { draft ->
        AlarmEditor(
            initial = draft,
            onPickSound = onPickSound,
            onDismiss = { editing = null },
            onSave = { onSave(it); editing = null },
        )
    }

    confirmDelete?.let { a ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.prayer_alarm_delete_q)) },
            text = { Text(a.label.ifBlank { stringResource(R.string.prayer_alarm_untitled) }) },
            confirmButton = {
                TextButton(onClick = { onDelete(a.id); confirmDelete = null }) { Text(stringResource(R.string.prayer_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.prayer_cancel)) } },
        )
    }
}

@Composable
private fun AlarmRow(
    alarm: Alarm,
    nextAt: Long?,
    zone: ZoneId,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onStartStop: () -> Unit,
) {
    val context = LocalContext.current
    // The file name is a content-resolver lookup: once per sound, not on every recomposition.
    val sound = remember(alarm.soundUri) {
        if (alarm.soundUri.isBlank()) context.getString(R.string.prayer_alarm_default_sound)
        else soundName(context, alarm.soundUri)
    }
    val (setting, status) = whenLines(context, alarm, nextAt, zone)
    val small = MaterialTheme.typography.labelMedium
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    // One fact per line, so a card reads top to bottom; every action on the last row.
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)) {
            // Time on the left, the name on the right — fixed sides whatever the UI language, each
            // in its own direction.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        defaultLabel(context, alarm),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textDirection = if (com.knotes.util.Bidi.isRtlLine(defaultLabel(context, alarm)) == true) TextDirection.Rtl else TextDirection.Ltr,
                        ),
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        alarm.label.trim(),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textDirection = if (com.knotes.util.Bidi.isRtlLine(alarm.label) == true) TextDirection.Rtl else TextDirection.Ltr,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Right,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(status, style = small, color = dim)
            if (setting.isNotEmpty()) Text(setting, style = small, color = dim)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = dim, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(4.dp))
                Text(sound, style = small, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                stringResource(R.string.prayer_alarm_repeat, alarm.repeatCount) + " · " +
                    if (alarm.volumePercent == 0) stringResource(R.string.prayer_alarm_volume_quietest)
                    else stringResource(R.string.prayer_alarm_volume, alarm.volumePercent),
                style = small,
                color = dim,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, bottom = 4.dp),
        ) {
            TextButton(onClick = onEdit) { Text(stringResource(R.string.prayer_edit)) }
            IconButton(onClick = onDuplicate) {
                Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.prayer_alarm_duplicate))
            }
            if (alarm.kind == AlarmKind.COUNTDOWN) {
                IconButton(onClick = onStartStop) {
                    Icon(
                        if (alarm.dueAtMillis == null) Icons.Filled.PlayArrow else Icons.Filled.Stop,
                        contentDescription = stringResource(
                            if (alarm.dueAtMillis == null) R.string.prayer_start else R.string.prayer_stop,
                        ),
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.prayer_delete))
            }
            Spacer(Modifier.weight(1f))
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
        }
    }
}

/** What the alarm is, when it has no label of its own. */
private fun defaultLabel(context: Context, a: Alarm): String = when (a.kind) {
    AlarmKind.CLOCK -> "%02d:%02d".format(a.hour, a.minute)
    AlarmKind.COUNTDOWN -> minutesLabel(context, a.durationMin)
    AlarmKind.PRAYER -> offsetLabel(context, a.prayer, a.offsetMin)
}

/**
 * Two lines: its days or state, and when it actually rings.
 *
 * "Not scheduled" is said out loud. An alarm that is on but has no next time — a countdown waiting
 * to be started, a prayer with no time today — otherwise looks identical to one that is armed.
 */
private fun whenLines(context: Context, a: Alarm, nextAt: Long?, zone: ZoneId): Pair<String, String> {
    fun days() = a.days.sorted().joinToString(" ") {
        it.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())
    }
    val setting = when (a.kind) {
        // The time itself is on the title line (see defaultLabel); here only what goes with it.
        AlarmKind.CLOCK -> if (a.days.isEmpty()) context.getString(R.string.prayer_alarm_once) else context.getString(R.string.prayer_alarm_on_days, days())
        AlarmKind.COUNTDOWN -> context.getString(
            if (a.dueAtMillis == null) R.string.prayer_alarm_not_started else R.string.prayer_alarm_running,
        )
        AlarmKind.PRAYER -> if (a.days.isEmpty()) "" else context.getString(R.string.prayer_alarm_on_days, days())
    }
    if (!a.enabled) return setting to context.getString(R.string.prayer_alarm_off)
    val at = nextAt ?: return setting to context.getString(R.string.prayer_alarm_not_scheduled)
    val t = java.time.LocalDateTime.ofInstant(Instant.ofEpochMilli(at), zone)
    val today = LocalDate.now(zone)
    val day = when (t.toLocalDate()) {
        today -> context.getString(R.string.prayer_today)
        today.plusDays(1) -> context.getString(R.string.prayer_tomorrow)
        else -> context.getString(
            R.string.prayer_alarm_weekday_date,
            t.toLocalDate().getDayOfWeek().getDisplayName(JavaTextStyle.SHORT, Locale.getDefault()),
            t.toLocalDate().dayOfMonth,
        )
    }
    return setting to context.getString(R.string.prayer_alarm_rings, day, "%02d:%02d".format(t.hour, t.minute))
}
