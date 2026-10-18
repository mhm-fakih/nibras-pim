package com.knotes.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import com.knotes.R

/**
 * Put text on the clipboard, and say so only where the system does not.
 *
 * Android 13 shows its own copy confirmation for every clipboard write. A Toast of our own on top of
 * it is two notices for one action — so the Toast is for older versions only, where nothing else
 * tells you the copy happened.
 *
 * [label] is what the system's clipboard UI calls the entry, not something the user types.
 */
object Clip {

    fun copy(context: Context, label: String, text: String) {
        if (text.isBlank()) return
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        runCatching {
            manager.setPrimaryClip(ClipData.newPlainText(label, text))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, context.getString(R.string.notes_copied), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
