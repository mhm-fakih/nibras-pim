package com.knotes.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.round

/**
 * A settings slider with − and + buttons either side of it.
 *
 * <p>A bare slider is a poor control for a *value*: on a phone the track is a couple of hundred
 * pixels wide, so one step of speed or one decibel is a few pixels of travel, and a tap lands
 * wherever your finger is rather than where you meant. Dragging to "1.05×" is a game of skill. The
 * buttons make the exact value reachable — the slider stays for coarse moves, which is what it is
 * actually good at.
 *
 * <p>Every value goes through [snap], including the slider's own, so dragging can't land between
 * steps and leave the buttons nudging from a fraction the label never showed.
 */
@Composable
fun StepSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    /** The smallest change a button makes, and the grid the slider snaps to. */
    step: Float,
    labelWidth: androidx.compose.ui.unit.Dp = 120.dp,
    // Last, so every call site can pass it as a trailing lambda.
    onChange: (Float) -> Unit,
) {
    val current = snap(value.coerceIn(min, max), min, step)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(labelWidth))
        IconButton(
            onClick = { onChange(snap((current - step).coerceIn(min, max), min, step)) },
            enabled = current > min,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease $label", modifier = Modifier.size(18.dp))
        }
        Slider(
            value = current,
            onValueChange = { onChange(snap(it.coerceIn(min, max), min, step)) },
            valueRange = min..max,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onChange(snap((current + step).coerceIn(min, max), min, step)) },
            enabled = current < max,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Increase $label", modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * The nearest multiple of [step] measured from [min], not from zero.
 *
 * From zero, a range like −20…50 in steps of 1 is the same grid — but 0.75…1.25 in steps of 0.05
 * is not, and the difference is the one the user notices: speed would settle on 0.75 and 0.80 but
 * never on the 0.775 the drag actually produced.
 */
internal fun snap(value: Float, min: Float, step: Float): Float {
    if (step <= 0f) return value
    return min + round((value - min) / step) * step
}
