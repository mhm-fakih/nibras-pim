package com.knotes.util

import android.view.KeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent

/**
 * Physical-keyboard submit for a multiline field: plain Enter still starts a new line, while
 * Ctrl+Enter, Shift+Enter or the numpad's Enter submit the form, as on a desktop.
 */
object SubmitKeys {
    fun isSubmit(keyCode: Int, ctrl: Boolean, shift: Boolean): Boolean =
        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            (keyCode == KeyEvent.KEYCODE_ENTER && (ctrl || shift))
}

/**
 * Calls [onSubmit] on a submit key (see [SubmitKeys]) and swallows the key, both its down and its
 * up, so no newline lands in the text. When [enabled] is false (an empty note) the key is still
 * swallowed: a submit that does nothing beats a stray newline.
 */
fun Modifier.submitOnEnterCombo(enabled: Boolean, onSubmit: () -> Unit): Modifier =
    onPreviewKeyEvent { e ->
        val n = e.nativeKeyEvent
        if (!SubmitKeys.isSubmit(n.keyCode, n.isCtrlPressed, n.isShiftPressed)) return@onPreviewKeyEvent false
        if (n.action == KeyEvent.ACTION_DOWN && n.repeatCount == 0 && enabled) onSubmit()
        true
    }
