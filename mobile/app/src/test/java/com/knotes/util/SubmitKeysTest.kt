package com.knotes.util

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubmitKeysTest {
    @Test fun plainEnterIsANewLine() = assertFalse(SubmitKeys.isSubmit(KeyEvent.KEYCODE_ENTER, ctrl = false, shift = false))
    @Test fun ctrlEnterSubmits() = assertTrue(SubmitKeys.isSubmit(KeyEvent.KEYCODE_ENTER, ctrl = true, shift = false))
    @Test fun shiftEnterSubmits() = assertTrue(SubmitKeys.isSubmit(KeyEvent.KEYCODE_ENTER, ctrl = false, shift = true))
    @Test fun numpadEnterSubmits() = assertTrue(SubmitKeys.isSubmit(KeyEvent.KEYCODE_NUMPAD_ENTER, ctrl = false, shift = false))
    @Test fun otherKeysDoNot() = assertFalse(SubmitKeys.isSubmit(KeyEvent.KEYCODE_A, ctrl = true, shift = true))
}
