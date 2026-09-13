package com.knotes.util

import java.util.Locale

/**
 * Counts with thousand separators — "22,385", not "22385".
 *
 * A five-digit run of bare digits has to be counted before it can be read, and these numbers exist
 * precisely to be read at a glance ("how much is left of this?").
 *
 * Locale.US rather than the device's, for the same reason the prayer widget pins its clock: the
 * galleries draw Arabic content on a phone that may be set to an Arabic locale, and the default
 * grouping there would render 1,576 in Arabic-Indic digits beside Western ones elsewhere in the
 * same line.
 */
fun grouped(n: Int): String = String.format(Locale.US, "%,d", n)
