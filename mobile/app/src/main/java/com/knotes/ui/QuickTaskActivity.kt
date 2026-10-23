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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.knotes.NoteRepository
import com.knotes.R
import com.knotes.SettingsStore
import com.knotes.util.autoDirection
import com.knotes.reminder.TodayJournalWidget
import com.knotes.reminder.TasksBoardWidget
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
 * capture look like a form to fill in. There is no Add button — the keyboard's own Done key saves.
 *
 * <h4>Typed text is never thrown away</h4>
 * The bar floats over a home screen, so a thumb that misses the field lands on wallpaper. That used
 * to discard whatever had been written. Two defences now, because the stray tap is only one of the
 * ways out:
 *
 *  1. A tap on the scrim closes the bar only while it is EMPTY. With something typed it is refused,
 *     and says so — the text is what the user is looking at, and having it vanish mid-sentence is
 *     alarming even when it could be recovered.
 *  2. Every keystroke is written to [QuickDraft] and read back when the bar reopens, which covers
 *     the exits that cannot be intercepted: Back, Home, and the system finishing an activity
 *     declared `noHistory`. Only a note reaching disk clears it.
 *
 * Serves both widgets, told apart by [EXTRA_TYPE]: tasks are captured at the top priority, because
 * that widget is a do-this-first list; journal entries at the ordinary default, because a day's
 * writing is not ranked. Saving writes a note in the app's ordinary format,
 * `<timestamp>~<type>~p<priority>~<category>.md`, and refreshes the widgets before finishing, so
 * the entry is on screen as the input disappears.
 */
class QuickTaskActivity : ComponentActivity() {

    // The app's own language below Android 13 — see AppLanguage.
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.knotes.util.AppLanguage.wrap(newBase))
    }

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

    // Restored from the draft, with the caret at the END of it. A restored draft whose cursor sits
    // at position 0 means the next thing typed lands in front of what was written, which reads as
    // the app having mangled it.
    var field by remember {
        val saved = QuickDraft.get(context, type)
        mutableStateOf(TextFieldValue(saved, TextRange(saved.length)))
    }
    val text = field.text
    var error by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    /*
     * Set the moment the note reaches disk, and it closes the draft for good.
     *
     * Clearing the draft in submit() was not enough on its own. Pressing the keyboard's Done makes
     * the IME finish its composing region, and that delivers a final onValueChange — AFTER the
     * action callback has already run. So the sequence was: save, clear the draft, then the
     * trailing keystroke event wrote the whole text straight back. The bar reopened pre-filled and
     * the next Done duplicated the record.
     */
    var submitted by remember { mutableStateOf(false) }
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
            // Order matters: stop accepting draft writes BEFORE clearing, or a trailing
            // onValueChange from the IME writes the text back over the cleared draft.
            submitted = true
            field = TextFieldValue("")
            // Only now: the text has become a note, so there is nothing left pending to restore.
            QuickDraft.clear(context, type)
            if (journal) TodayJournalWidget.refresh(context)
            else {
                TodayTasksWidget.refresh(context)
                TasksBoardWidget.refresh(context)
            }
            onClose()
        }.onFailure {
            // Stay open and say so. The text is still in the field, which is the only copy of it.
            error = it.message?.takeIf { m -> m.isNotBlank() }
                ?: context.getString(R.string.notes_quick_write_failed)
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

    /*
     * Scrim: tapping off the bar closes it ONLY while it is empty.
     *
     * With something typed the tap is refused, because the bar sits over a home screen and the
     * target is a one-line field with wallpaper all round it — a thumb that misses is the ordinary
     * case, not the exceptional one, and it used to cost whatever had been written.
     *
     * Refused rather than "dismiss but keep the draft": the text is on screen, it is what the user
     * is looking at, and having it vanish mid-sentence is alarming even when it is recoverable.
     * Back still leaves, and the draft is kept for that.
     *
     * No ripple — this is a background, not a button.
     */
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (text.isBlank()) onClose()
                    else hint = context.getString(R.string.notes_quick_kept_hint)
                },
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
                    if (journal) stringResource(R.string.notes_quick_journal_today) else stringResource(R.string.notes_quick_task_priority, priority),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = field,
                    onValueChange = {
                        field = it
                        error = null
                        hint = null
                        // Every keystroke, so no exit path can outrun it — including the system
                        // finishing this activity, which is declared noHistory. Except once the
                        // note is saved: see `submitted`.
                        if (!submitted) QuickDraft.put(context, type, it.text)
                    },
                    placeholder = {
                        Text(stringResource(if (journal) R.string.notes_quick_what_happened else R.string.notes_quick_what_needs_doing))
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
                // Shown only after a tap that was refused, so the bar does not look stuck.
                hint?.takeIf { error == null }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
