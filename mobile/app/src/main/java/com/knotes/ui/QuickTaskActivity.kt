package com.knotes.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.knotes.NoteRepository
import com.knotes.SettingsStore
import com.knotes.util.autoDirection
import com.knotes.reminder.TodayJournalWidget
import com.knotes.reminder.TodayTasksWidget

/**
 * A one-line input over the home screen, for capturing straight from a widget.
 *
 * It exists because **a widget cannot contain a text field**: `RemoteViews` supports no editable
 * view on any Android version, so a widget's "Add…" bar is a TextView that opens this instead. This
 * is as close to typing in the widget as the platform allows.
 *
 * Presented as a bar sitting ON the keyboard rather than as a centred dialog: the gesture is "tap,
 * type, enter", and a card in the middle of the screen with Cancel and Add buttons made a one-line
 * capture look like a form to fill in. There is no Add button — the keyboard's own Done key saves,
 * and tapping away cancels, which is what the scrim is for.
 *
 * Serves both widgets, told apart by [EXTRA_TYPE]: tasks are captured at the top priority, because
 * that widget is a do-this-first list; journal entries at the ordinary default, because a day's
 * writing is not ranked. Saving writes a note in the app's ordinary format,
 * `<timestamp>~<type>~p<priority>~<category>.md`, and refreshes the widgets before finishing, so
 * the entry is on screen as the input disappears.
 */
class QuickTaskActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = intent?.getStringExtra(EXTRA_TYPE)?.takeIf { it.isNotBlank() }
            ?: TodayTasksWidget.TASK_TYPE
        setContent {
            KnotesTheme {
                QuickCaptureBar(type = type, onClose = { finish() })
            }
        }
    }

    companion object {
        /** Which note type to capture — "T" or "J". Absent means a task, the original behaviour. */
        const val EXTRA_TYPE = "com.knotes.quick.TYPE"
    }
}

@Composable
private fun QuickCaptureBar(type: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    val journal = type.equals(TodayJournalWidget.JOURNAL_TYPE, ignoreCase = true)
    // A task is captured at the priority its widget lists; a journal entry at the ordinary default,
    // since that widget shows every priority and ranking a diary entry means nothing.
    val priority = if (journal) settings.value.defaultPriority else settings.value.topPriority

    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    fun submit() {
        val body = text.trim()
        if (body.isEmpty()) {
            onClose()
            return
        }
        // Written on the calling thread: it is one small file, and the alternative — finishing
        // before the write lands — is how a task gets typed and silently lost.
        val saved = runCatching {
            NoteRepository(settings).saveNew(
                text = body,
                type = type,
                priority = priority,
                // The same category a quick note gets, so a task captured here is filed like
                // anything else captured on the phone rather than under its own scheme.
                category = settings.value.effectiveDefaultCategory,
            )
        }
        saved.onSuccess {
            if (journal) TodayJournalWidget.refresh(context) else TodayTasksWidget.refresh(context)
            onClose()
        }.onFailure {
            // Stay open and say so. The text is still in the field, which is the only copy of it.
            error = it.message?.takeIf { m -> m.isNotBlank() }
                ?: "Could not write the note. Check that file access is still granted."
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

    // Scrim: tapping anywhere off the bar cancels. No ripple — this is a background, not a button.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            )
            .imePadding(),
        // Bottom, not Center: the bar rides directly on top of the keyboard, where the thing you
        // are typing into belongs. Centred, it sat in open space with the keyboard below it and
        // read as a dialog that had opened over the home screen.
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                // Absorbs taps so they don't reach the scrim behind and cancel.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    if (journal) "Journal · today" else "Task · p$priority",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    placeholder = {
                        Text(if (journal) "What happened?" else "What needs doing?")
                    },
                    // Follows what is typed: an Arabic entry right-aligns as it is written, an
                    // English one stays left, and the caret sits at the end of the text either way.
                    // The same `autoDirection` the file and note lists use rather than a test on
                    // the string — it cannot disagree with the direction the text is laid out in,
                    // and it re-resolves on every keystroke, which is what makes an empty field
                    // that starts neutral flip the moment the first Arabic letter lands.
                    textStyle = MaterialTheme.typography.bodyLarge.autoDirection(),
                    singleLine = true,
                    // Done saves. There is no Add button: one line plus a keyboard already has a
                    // commit key, and a button beside it is a second way to do the same thing that
                    // has to be reached for.
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    isError = error != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                error?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
