package com.knotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.knotes.R

/**
 * Settings → General: the theme's base colour. A row of swatches, the phone's own wallpaper colours,
 * and a hex field for any other colour. Applied on tap (see [ThemeColor]); there is nothing to save.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeColorPicker() {
    val context = LocalContext.current
    val stored by ThemeColor.value.collectAsState()
    val current = stored ?: remember { ThemeColor.load(context) }
    var hex by remember(current) {
        mutableStateOf(if (current == ThemeColor.PHONE) "" else "%06X".format(current and 0xFFFFFF))
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_theme_color), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemeColor.PRESETS.forEach { argb ->
                Swatch(Color(argb), selected = current == argb, label = "#%06X".format(argb and 0xFFFFFF)) {
                    ThemeColor.set(context, argb)
                }
            }
            val phoneLabel = stringResource(R.string.settings_theme_phone)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .border(
                        if (current == ThemeColor.PHONE) 3.dp else 1.dp,
                        if (current == ThemeColor.PHONE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        CircleShape,
                    )
                    .clickable(role = Role.RadioButton) { ThemeColor.set(context, ThemeColor.PHONE) }
                    .semantics { contentDescription = phoneLabel },
            ) {
                Icon(Icons.Filled.Wallpaper, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(
            value = hex,
            onValueChange = { v ->
                hex = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                if (hex.length == 6) ThemeColor.set(context, (0xFF000000 or hex.toLong(16)).toInt())
            },
            label = { Text(stringResource(R.string.settings_theme_hex)) },
            prefix = { Text("#") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.settings_theme_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .background(color, CircleShape)
            .border(if (selected) 3.dp else 0.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
    }
}
