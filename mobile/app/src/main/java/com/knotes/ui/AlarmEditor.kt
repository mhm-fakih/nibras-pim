package com.knotes.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knotes.R
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
 * Sound comes right under the name, above the kind, so it is seen without scrolling — at the bottom
 * of the dialog it went unnoticed. Repeat and volume, common to every kind too, stay at the bottom.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmEditor(
    initial: Alarm,
    onPickSound: ((String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
) {
    val context = LocalContext.current
    var a by remember { mutableStateOf(initial) }
    var customMin by remember { mutableStateOf(initial.durationMin.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial.label.isBlank() && initial.days.isEmpty()) R.string.prayer_alarm_new
                    else R.string.prayer_alarm_default_title,
                ),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(a) }) { Text(stringResource(R.string.prayer_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.prayer_cancel)) } },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = a.label,
                    onValueChange = { a = a.copy(label = it) },
                    label = { Text(stringResource(R.string.prayer_alarm_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Label(stringResource(R.string.prayer_alarm_sound))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (a.soundUri.isBlank()) stringResource(R.string.prayer_alarm_default_sound)
                        else soundName(context, a.soundUri),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onPickSound { uri -> a = a.copy(soundUri = uri) } }) {
                        Text(stringResource(R.string.prayer_alarm_choose))
                    }
                    if (a.soundUri.isNotBlank()) {
                        TextButton(onClick = { a = a.copy(soundUri = "") }) { Text(stringResource(R.string.prayer_alarm_default)) }
                    }
                }

                Label(stringResource(R.string.prayer_alarm_kind))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AlarmKind.entries.forEach { k ->
                        FilterChip(
                            selected = a.kind == k,
                            onClick = { a = a.copy(kind = k) },
                            label = { Text(stringResource(kindLabel(k))) },
                        )
                    }
                }

                when (a.kind) {
                    AlarmKind.CLOCK -> {
                        Label(stringResource(R.string.prayer_alarm_kind_time))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Equal weights. Without them the first field takes its preferred
                            // width and the second gets whatever is left — which was a sliver.
                            NumberBox(stringResource(R.string.prayer_alarm_hour), a.hour, 0, 23, Modifier.weight(1f)) { a = a.copy(hour = it) }
                            NumberBox(stringResource(R.string.prayer_alarm_minute), a.minute, 0, 59, Modifier.weight(1f)) { a = a.copy(minute = it) }
                        }
                        DayPicker(a.days) { a = a.copy(days = it) }
                    }

                    AlarmKind.COUNTDOWN -> {
                        Label(stringResource(R.string.prayer_alarm_duration))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Alarm.COUNTDOWN_PRESETS_MIN.forEach { m ->
                                FilterChip(
                                    selected = a.durationMin == m,
                                    onClick = { a = a.copy(durationMin = m); customMin = m.toString() },
                                    label = { Text(minutesLabel(context, m)) },
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
                            label = { Text(stringResource(R.string.prayer_alarm_or_minutes)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    AlarmKind.PRAYER -> {
                        Label(stringResource(R.string.prayer_alarm_kind_prayer))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Prayer.entries.forEach { p ->
                                FilterChip(
                                    selected = a.prayer == p,
                                    onClick = { a = a.copy(prayer = p) },
                                    label = { Text(stringResource(p.labelRes)) },
                                )
                            }
                        }
                        Label(stringResource(R.string.prayer_alarm_offset))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Alarm.PRAYER_OFFSETS_MIN.forEach { o ->
                                FilterChip(
                                    selected = a.offsetMin == o,
                                    onClick = { a = a.copy(offsetMin = o) },
                                    label = {
                                        Text(
                                            if (o == 0) stringResource(R.string.prayer_alarm_offset_at)
                                            else if (o < 0) "-${minutesLabel(context, -o)}"
                                            else "+${minutesLabel(context, o)}",
                                        )
                                    },
                                )
                            }
                        }
                        Text(
                            offsetLabel(context, a.prayer, a.offsetMin),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        DayPicker(a.days) { a = a.copy(days = it) }
                    }
                }

                Label(stringResource(R.string.prayer_alarm_repeat, a.repeatCount))
                Slider(
                    value = a.repeatCount.toFloat(),
                    onValueChange = { a = a.copy(repeatCount = it.toInt().coerceIn(1, 20)) },
                    valueRange = 1f..20f,
                    steps = 18,
                )

                Label(if (a.volumePercent == 0) stringResource(R.string.prayer_alarm_volume_quietest) else stringResource(R.string.prayer_alarm_volume, a.volumePercent))
                Slider(
                    value = a.volumePercent.toFloat(),
                    onValueChange = { a = a.copy(volumePercent = it.toInt().coerceIn(0, 100)) },
                    valueRange = 0f..100f,
                )
            }
        },
    )
}

/** The picked file's own name ("adhan.mp3"), or the last part of the link when it can't be read. */
internal fun soundName(context: Context, uri: String): String {
    val parsed = Uri.parse(uri)
    val name = runCatching {
        context.contentResolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
    return name ?: parsed.lastPathSegment?.substringAfterLast('/') ?: uri
}

private fun kindLabel(k: AlarmKind): Int = when (k) {
    AlarmKind.CLOCK -> R.string.prayer_alarm_kind_time
    AlarmKind.COUNTDOWN -> R.string.prayer_alarm_kind_countdown
    AlarmKind.PRAYER -> R.string.prayer_alarm_kind_prayer
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
    Label(stringResource(if (days.isEmpty()) R.string.prayer_alarm_days_once else R.string.prayer_alarm_days))
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
