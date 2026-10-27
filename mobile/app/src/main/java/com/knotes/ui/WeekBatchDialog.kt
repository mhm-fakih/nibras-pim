package com.knotes.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.knotes.R
import com.knotes.nibras.WeekBatch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BATCH_DATE: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val BATCH_TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

/**
 * "Add a week": several calendar entries typed as lines (see [WeekBatch] for the format), checked
 * live underneath, then added together. With more than one week each planner repeats weekly — a
 * university timetable is typed once for the whole term.
 */
@Composable
fun WeekBatchDialog(
    initialType: String,
    onDismiss: () -> Unit,
    onAdd: (entries: List<WeekBatch.Entry>, defaultType: String, from: LocalDate, weeks: Int) -> Unit,
) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(initialType) }
    var fromDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var weeks by rememberSaveable { mutableStateOf(1) }
    val from = LocalDate.ofEpochDay(fromDay)
    val lines = remember(text) { WeekBatch.parse(text) }
    val entries = lines.flatMap { it.entries }
    val ok = entries.isNotEmpty() && lines.none { it.problem != null }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.server_calendar_batch_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.server_calendar_batch_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = {
                    Text("p mon 10:00 Calculus\np mon,wed 8:00-9:30 Physics\np tue-thu 14 Lab", fontFamily = FontFamily.Monospace)
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
            )

            // Lines without p/j take this type.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.server_calendar_type), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(72.dp))
                FilterChip(selected = type == "P", onClick = { type = "P" }, label = { Text(stringResource(R.string.server_calendar_type_plan)) })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = type == "J", onClick = { type = "J" }, label = { Text(stringResource(R.string.server_calendar_type_journal)) })
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.server_calendar_batch_from), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(72.dp))
                TextButton(onClick = {
                    DatePickerDialog(context, { _, y, m, d -> fromDay = LocalDate.of(y, m + 1, d).toEpochDay() },
                        from.year, from.monthValue - 1, from.dayOfMonth).show()
                }) { Text(from.format(BATCH_DATE)) }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.server_calendar_batch_repeat), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(72.dp))
                IconButton(onClick = { weeks = (weeks - 1).coerceAtLeast(1) }, enabled = weeks > 1) {
                    Icon(Icons.Filled.Remove, stringResource(R.string.server_calendar_batch_fewer_weeks))
                }
                Text(
                    if (weeks == 1) stringResource(R.string.server_calendar_batch_once)
                    else stringResource(R.string.server_calendar_batch_weeks, weeks, from.plusWeeks(weeks.toLong()).minusDays(1).format(BATCH_DATE)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { weeks = (weeks + 1).coerceAtMost(52) }, enabled = weeks < 52) {
                    Icon(Icons.Filled.Add, stringResource(R.string.server_calendar_batch_more_weeks))
                }
            }
            if (weeks > 1 && entries.any { (it.type ?: type) == "J" }) {
                Text(
                    stringResource(R.string.server_calendar_batch_journal_once),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // What each line becomes, or what's wrong with it.
            if (lines.isNotEmpty()) {
                Column(modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    lines.forEach { line ->
                        val problem = line.problem
                        if (problem != null) {
                            Text(
                                stringResource(R.string.server_calendar_batch_line_problem, line.number, stringResource(problemText(problem))),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else line.entries.forEach { e ->
                            Text(
                                "${e.type ?: type} · ${WeekBatch.firstDate(from, e.day).format(BATCH_DATE)} " +
                                    "${e.start.format(BATCH_TIME)}–${e.end.format(BATCH_TIME)} · ${e.title}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.server_cancel)) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onAdd(entries, type, from, weeks) }, enabled = ok) {
                    Text(stringResource(R.string.server_calendar_batch_add, entries.size))
                }
            }
        }
    }
}

private fun problemText(p: WeekBatch.Problem): Int = when (p) {
    WeekBatch.Problem.NO_DAY -> R.string.server_calendar_batch_no_day
    WeekBatch.Problem.NO_TIME -> R.string.server_calendar_batch_no_time
    WeekBatch.Problem.BAD_END -> R.string.server_calendar_batch_bad_end
    WeekBatch.Problem.NO_TITLE -> R.string.server_calendar_batch_no_title
}
