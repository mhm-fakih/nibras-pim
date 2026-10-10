package com.knotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.knotes.alarm.Alarm
import com.knotes.alarm.AlarmKind
import com.knotes.alarm.minutesLabel
import com.knotes.alarm.offsetLabel
import com.knotes.prayer.Prayer
import java.time.DayOfWeek
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * Add or change one alarm.
 *
 * The kind is a row of chips at the top and everything below it follows. Switching kind keeps what
 * was typed under the other kinds — the fields are all on the one [Alarm], so going Clock → Prayer
 * → Clock does not lose the time you set.
 *
 * Sound, repeat and volume are common to every kind and stay at the bottom, in that order: what it
 * plays, how many times, how loud.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmEditor(
    initial: Alarm,
    onPickSound: ((String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
) {
    var a by remember { mutableStateOf(initial) }
    var customMin by remember { mutableStateOf(initial.durationMin.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.label.isBlank() && initial.days.isEmpty()) "New alarm" else "Alarm") },
        confirmButton = { TextButton(onClick = { onSave(a) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = a.label,
                    onValueChange = { a = a.copy(label = it) },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Label("Kind")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AlarmKind.entries.forEach { k ->
                        FilterChip(
                            selected = a.kind == k,
                            onClick = { a = a.copy(kind = k) },
                            label = { Text(kindLabel(k)) },
                        )
                    }
                }

                when (a.kind) {
                    AlarmKind.CLOCK -> {
                        Label("Time")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Equal weights. Without them the first field takes its preferred
                            // width and the second gets whatever is left — which was a sliver.
                            NumberBox("Hour", a.hour, 0, 23, Modifier.weight(1f)) { a = a.copy(hour = it) }
                            NumberBox("Minute", a.minute, 0, 59, Modifier.weight(1f)) { a = a.copy(minute = it) }
                        }
                        DayPicker(a.days) { a = a.copy(days = it) }
                    }

                    AlarmKind.COUNTDOWN -> {
                        Label("Duration")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Alarm.COUNTDOWN_PRESETS_MIN.forEach { m ->
                                FilterChip(
                                    selected = a.durationMin == m,
                                    onClick = { a = a.copy(durationMin = m); customMin = m.toString() },
                                    label = { Text(minutesLabel(m)) },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = customMin,
                            onValueChange = {
                                customMin = it.filter(Char::isDigit).take(4)
                                customMin.toIntOrNull()?.let { n ->
                                    if (n > 0) a = a.copy(durationMin = n)
                                }
                            },
                            label = { Text("Or minutes") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    AlarmKind.PRAYER -> {
                        Label("Prayer")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Prayer.entries.forEach { p ->
                                FilterChip(
                                    selected = a.prayer == p,
                                    onClick = { a = a.copy(prayer = p) },
                                    label = { Text(p.label) },
                                )
                            }
                        }
                        Label("Offset — negative is before")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Alarm.PRAYER_OFFSETS_MIN.forEach { o ->
                                FilterChip(
                                    selected = a.offsetMin == o,
                                    onClick = { a = a.copy(offsetMin = o) },
                                    label = { Text(if (o == 0) "at" else if (o < 0) "-${minutesLabel(-o)}" else "+${minutesLabel(o)}") },
                                )
                            }
                        }
                        Text(
                            offsetLabel(a.prayer, a.offsetMin),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        DayPicker(a.days) { a = a.copy(days = it) }
                    }
                }

                Label("Sound")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onPickSound { uri -> a = a.copy(soundUri = uri) } }) {
                        Text("Choose…")
                    }
                    if (a.soundUri.isNotBlank()) {
                        TextButton(onClick = { a = a.copy(soundUri = "") }) { Text("Default") }
                    }
                }
                Text(
                    if (a.soundUri.isBlank()) "Default alarm sound" else a.soundUri,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )

                Label("Repeat: ${a.repeatCount}x")
                Slider(
                    value = a.repeatCount.toFloat(),
                    onValueChange = { a = a.copy(repeatCount = it.toInt().coerceIn(1, 20)) },
                    valueRange = 1f..20f,
                    steps = 18,
                )

                Label("Volume: ${a.volumePercent}%")
                Slider(
                    value = a.volumePercent.toFloat(),
                    onValueChange = { a = a.copy(volumePercent = it.toInt().coerceIn(0, 100)) },
                    valueRange = 0f..100f,
                )
            }
        },
    )
}

private fun kindLabel(k: AlarmKind) = when (k) {
    AlarmKind.CLOCK -> "Time"
    AlarmKind.COUNTDOWN -> "Countdown"
    AlarmKind.PRAYER -> "Prayer"
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun NumberBox(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it.filter(Char::isDigit).take(2)
            text.toIntOrNull()?.let { n -> if (n in min..max) onChange(n) }
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
    )
}

/** Days of the week, Monday-first. None selected means "once", which the row spells out. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayPicker(days: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    Label(if (days.isEmpty()) "Days — none means once" else "Days")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DayOfWeek.entries.forEach { d ->
            FilterChip(
                selected = d in days,
                onClick = { onChange(if (d in days) days - d else days + d) },
                label = { Text(d.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())) },
            )
        }
    }
}
