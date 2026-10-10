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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
                Icon(Icons.Filled.Add, contentDescription = "Add an alarm")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text("Alarms", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${alarms.count { it.enabled }} on",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!exactAllowed) {
                // Said once, plainly. Without the permission Android delivers these late, and an
                // alarm quietly firing ten minutes after its time is worse than one that says why.
                Text(
                    "Exact alarms are not permitted for this app, so these may fire late. " +
                        "Grant \"Alarms & reminders\" in Android settings.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            if (alarms.isEmpty()) {
                Text(
                    "No alarms yet. The + button adds one: a time, a countdown, or an offset from " +
                        "a prayer time.",
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
            title = { Text("Delete this alarm?") },
            text = { Text(a.label.ifBlank { "Untitled alarm" }) },
            confirmButton = {
                TextButton(onClick = { onDelete(a.id); confirmDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
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
    onDelete: () -> Unit,
    onStartStop: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    alarm.label.ifBlank { defaultLabel(alarm) },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    whenLine(alarm, nextAt, zone),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (alarm.kind == AlarmKind.COUNTDOWN) {
                IconButton(onClick = onStartStop) {
                    Icon(
                        if (alarm.dueAtMillis == null) Icons.Filled.PlayArrow else Icons.Filled.Stop,
                        contentDescription = if (alarm.dueAtMillis == null) "Start" else "Stop",
                    )
                }
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete")
            }
        }
        Row(modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)) {
            TextButton(onClick = onEdit) { Text("Edit") }
        }
    }
}

/** What the alarm is, when it has no label of its own. */
private fun defaultLabel(a: Alarm): String = when (a.kind) {
    AlarmKind.CLOCK -> "%02d:%02d".format(a.hour, a.minute)
    AlarmKind.COUNTDOWN -> minutesLabel(a.durationMin)
    AlarmKind.PRAYER -> offsetLabel(a.prayer, a.offsetMin)
}

/**
 * The second line: what it is set to, and when that actually lands.
 *
 * "Not scheduled" is said out loud. An alarm that is on but has no next time — a countdown waiting
 * to be started, a prayer with no time today — otherwise looks identical to one that is armed.
 */
private fun whenLine(a: Alarm, nextAt: Long?, zone: ZoneId): String {
    val setting = when (a.kind) {
        AlarmKind.CLOCK -> "%02d:%02d".format(a.hour, a.minute) +
            if (a.days.isEmpty()) " · once" else " · " + a.days.sorted().joinToString(" ") {
                it.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())
            }
        AlarmKind.COUNTDOWN ->
            minutesLabel(a.durationMin) + if (a.dueAtMillis == null) " · not started" else " · running"
        AlarmKind.PRAYER -> offsetLabel(a.prayer, a.offsetMin) +
            if (a.days.isEmpty()) "" else " · " + a.days.sorted().joinToString(" ") {
                it.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())
            }
    }
    if (!a.enabled) return "$setting · off"
    val at = nextAt ?: return "$setting · not scheduled"
    val t = java.time.LocalDateTime.ofInstant(Instant.ofEpochMilli(at), zone)
    val today = LocalDate.now(zone)
    val day = when (t.toLocalDate()) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> t.toLocalDate().getDayOfWeek().getDisplayName(JavaTextStyle.SHORT, Locale.getDefault()) +
            " " + t.toLocalDate().dayOfMonth
    }
    return "$setting · rings $day ${"%02d:%02d".format(t.hour, t.minute)}"
}
