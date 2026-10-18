package com.knotes.util

import android.content.Context
import android.net.Uri
import com.knotes.R

/**
 * Sounds shipped inside the app, played when the user hasn't chosen a file.
 *
 * A fresh install used to fall back to the system alarm/notification tone, so an athan sounded
 * like an alarm clock until the user found, copied and picked an audio file themselves. These make
 * the features right on first run; choosing a file in settings still overrides them.
 */
object DefaultSounds {

    /** The athan recited by Mohammad Kazem-Zadeh, mono OGG. */
    fun athan(context: Context): Uri = raw(context, R.raw.default_athan)

    /** A short airport-style "ding" (Mixkit, free licence) for the hourly chime. */
    fun chime(context: Context): Uri = raw(context, R.raw.default_chime)

    private fun raw(context: Context, id: Int): Uri =
        Uri.parse("android.resource://${context.packageName}/$id")
}
