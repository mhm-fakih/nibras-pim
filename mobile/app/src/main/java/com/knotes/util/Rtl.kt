package com.knotes.util

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection

/**
 * Text alignment that follows the CONTENT rather than the app's locale.
 *
 * An Arabic file name in a left-to-right layout is drawn hard against the left edge with its
 * punctuation flung to the wrong end — legible, but wrong, and jarring next to the note cards which
 * have always aligned themselves properly.
 *
 * [TextDirection.Content] resolves each paragraph's direction from its first strong directional
 * character, and [TextAlign.Start] then means "the start of that direction" — so Arabic
 * right-aligns and English left-aligns with no per-string test at all. That is why this is
 * preferred to the older `if (isRtl) TextAlign.Right else TextAlign.Start` scattered elsewhere: it
 * can't disagree with the direction the text is actually laid out in, and it costs nothing to apply
 * to a name that turns out to be English.
 */
fun TextStyle.autoDirection(): TextStyle =
    copy(textDirection = TextDirection.Content, textAlign = TextAlign.Start)
