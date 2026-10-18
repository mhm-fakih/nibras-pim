package com.knotes.ui

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.knotes.R
import com.knotes.util.AppLanguage

/**
 * English / عربي / Français, applied the moment one is tapped — the first page of the setup tour
 * and the first row of Settings.
 *
 * Each language is written in itself, so whoever needs to switch can find their own language
 * whatever the screen is currently in. With nothing chosen yet the phone's language is ticked when
 * it is one of the three, and nothing otherwise.
 */
@Composable
fun LanguagePicker(modifier: Modifier = Modifier, showTitle: Boolean = true) {
    val context = LocalContext.current
    val activity = context as? Activity
    val chosen = AppLanguage.current(context)
        ?: AppLanguage.Lang.entries.firstOrNull {
            java.util.Locale.forLanguageTag(it.tag).language == java.util.Locale.getDefault().language
        }
    Column(modifier.selectableGroup()) {
        if (showTitle) Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleSmall)
        AppLanguage.Lang.entries.forEach { lang ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = lang == chosen,
                        role = Role.RadioButton,
                        onClick = { if (lang != chosen && activity != null) AppLanguage.set(activity, lang) },
                    )
                    .padding(vertical = 2.dp),
            ) {
                RadioButton(selected = lang == chosen, onClick = null)
                Text(lang.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Text(
            stringResource(R.string.language_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
