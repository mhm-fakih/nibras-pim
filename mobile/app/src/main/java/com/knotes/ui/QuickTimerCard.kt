package com.knotes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.knotes.R
import com.knotes.alarm.Alarm
import com.knotes.alarm.QuickTimer
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The quick timer: a 60-minute dial and one icon button, in at most a third of the screen.
 *
 * Drag around the dial (clockwise from 12, like a kitchen timer) to set 1-60 minutes, then press
 * the button or tap the dial's middle. While it runs, the coloured wedge shrinks towards 12 and the
 * middle counts down; the same button (now a stop icon) or the middle cancels it.
 *
 * [quick] is the stored timer (see [QuickTimer]): running when it has a due time.
 */
@Composable
fun QuickTimerCard(
    quick: Alarm?,
    onStart: (Int) -> Unit,
    onStop: () -> Unit,
) {
    val due = quick?.dueAtMillis
    val running = due != null
    var minutes by remember { mutableIntStateOf(quick?.durationMin?.coerceIn(1, 60) ?: QuickTimer.DEFAULT_MIN) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(due) {
        while (due != null) {
            now = System.currentTimeMillis()
            if (now >= due) break
            delay(1000L - now % 1000L)
        }
    }
    val remainingMs = if (due != null) (due - now).coerceAtLeast(0L) else 0L
    val toggle = { if (running) onStop() else onStart(minutes) }

    // A third of the screen, padding included; never wider than the space beside the button.
    val third = LocalConfiguration.current.screenHeightDp.dp / 3
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().height(third).padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val dial = minOf(maxHeight, maxWidth - 96.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimerDial(
                minutes = minutes,
                remainingMs = if (running) remainingMs else null,
                endsAt = due,
                size = dial,
                onMinutes = { minutes = it },
                onCenterTap = toggle,
            )
            Spacer(Modifier.width(20.dp))
            FilledIconButton(
                onClick = toggle,
                modifier = Modifier.size(56.dp),
                colors = if (running) IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ) else IconButtonDefaults.filledIconButtonColors(),
            ) {
                Icon(
                    if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (running) R.string.qt_stop_cd else R.string.qt_start_cd),
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

/**
 * The dial itself. Idle, the wedge shows the chosen minutes and follows the finger; running, it
 * shows the time left on the same 60-minute face, so 10 minutes left looks the same however long the
 * timer was set for.
 */
@Composable
private fun TimerDial(
    minutes: Int,
    remainingMs: Long?,
    endsAt: Long?,
    size: Dp,
    onMinutes: (Int) -> Unit,
    onCenterTap: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val running = remainingMs != null
    val currentMinutes by rememberUpdatedState(minutes)
    val setMinutes by rememberUpdatedState(onMinutes)
    val tapCenter by rememberUpdatedState(onCenterTap)

    val track = MaterialTheme.colorScheme.outlineVariant
    val wedge = if (running) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val tick = MaterialTheme.colorScheme.outline
    val knob = MaterialTheme.colorScheme.onPrimary

    /** Minutes for a touch at [p] in a dial of [w] × [h]: clockwise from 12, 1..60, no wrap-around. */
    fun minutesAt(p: Offset, w: Float, h: Float): Int {
        val deg = Math.toDegrees(atan2((p.x - w / 2).toDouble(), (h / 2 - p.y).toDouble())).let { if (it < 0) it + 360 else it }
        var m = ceil(deg / 6.0).toInt().coerceIn(1, 60)
        // Crossing 12 does not jump from 60 to 1 (or back): a dial you drag past the top stays full.
        if (currentMinutes >= 50 && m <= 10) m = 60
        if (currentMinutes <= 10 && m >= 50) m = 1
        return m
    }

    fun update(m: Int) {
        if (m != currentMinutes) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            setMinutes(m)
        }
    }

    val cd = stringResource(if (running) R.string.qt_stop_cd else R.string.qt_start_cd)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size).semantics { contentDescription = cd }) {
        Canvas(
            modifier = Modifier
                .size(size)
                .pointerInput(running) {
                    detectTapGestures { p ->
                        val r = minOf(this.size.width, this.size.height) / 2f
                        if (hypot(p.x - this.size.width / 2f, p.y - this.size.height / 2f) < r * 0.55f) tapCenter()
                        else if (!running) update(minutesAt(p, this.size.width.toFloat(), this.size.height.toFloat()))
                    }
                }
                .pointerInput(running) {
                    if (running) return@pointerInput
                    detectDragGestures(
                        onDragStart = { p -> update(minutesAt(p, this.size.width.toFloat(), this.size.height.toFloat())) },
                    ) { change, _ ->
                        change.consume()
                        update(minutesAt(change.position, this.size.width.toFloat(), this.size.height.toFloat()))
                    }
                },
        ) {
            val stroke = this.size.minDimension * 0.13f
            val r = this.size.minDimension / 2f - stroke / 2f
            val topLeft = Offset(center.x - r, center.y - r)
            val arc = Size(r * 2, r * 2)
            drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
            val sweep = if (remainingMs != null) (remainingMs / 3_600_000f * 360f).coerceIn(0f, 360f) else minutes * 6f
            drawArc(wedge, -90f, sweep, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Butt))
            // A mark every 5 minutes, longer at the quarters, inside the ring.
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0 - 90.0)
                val outer = r - stroke / 2f - 3.dp.toPx()
                val inner = outer - if (i % 3 == 0) 9.dp.toPx() else 5.dp.toPx()
                drawLine(
                    tick,
                    Offset(center.x + inner * cos(a).toFloat(), center.y + inner * sin(a).toFloat()),
                    Offset(center.x + outer * cos(a).toFloat(), center.y + outer * sin(a).toFloat()),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            if (remainingMs == null) {
                val a = Math.toRadians(minutes * 6.0 - 90.0)
                drawCircle(knob, stroke * 0.32f, Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat()))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (remainingMs != null) {
                val s = ceil(remainingMs / 1000.0).toLong()
                Text(
                    "%d:%02d".format(s / 60, s % 60),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                endsAt?.let {
                    val t = java.time.LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
                    Text(
                        stringResource(R.string.qt_ends_at, "%02d:%02d".format(t.hour, t.minute)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text("$minutes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.qt_min_unit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
