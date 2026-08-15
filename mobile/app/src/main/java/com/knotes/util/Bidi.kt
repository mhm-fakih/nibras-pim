package com.knotes.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection

/**
 * Text direction decided **per line**, from the first strongly-directional letter on it.
 *
 * <h3>What was wrong</h3>
 * Direction was a property of the whole note, set by `RTL.containsMatchIn(text)` — true if the text
 * contained a single Arabic character *anywhere*. One quoted Arabic word, one Arabic name, one
 * `'ar` tag, and an entire English note laid itself out right-to-left with its punctuation thrown
 * to the wrong end.
 *
 * <h3>The rule</h3>
 * A line's direction is that of its first **strong** character. Digits, punctuation, spaces,
 * brackets and emoji are neutral — they take their direction from what surrounds them, so a line
 * opening with `"1." ` or `(` is decided by the first real letter after it, which is what a reader
 * would say the line is written in. A line with no strong character at all — blank, or all digits —
 * inherits the previous line's direction rather than snapping to a default, so a numbered list
 * doesn't alternate sides.
 *
 * Per line rather than per paragraph because these notes genuinely mix: an Arabic quotation under
 * an English heading is one note, and both halves should read correctly.
 */
object Bidi {

    /** Arabic, Arabic Supplement, Extended-A, presentation forms, plus Hebrew. */
    private fun isRtlChar(c: Char): Boolean =
        c in '֐'..'׿' ||      // Hebrew
            c in '؀'..'ۿ' ||  // Arabic
            c in 'ݐ'..'ݿ' ||  // Arabic Supplement
            c in 'ࢠ'..'ࣿ' ||  // Arabic Extended-A
            c in 'יִ'..'﷿' ||  // Hebrew + Arabic presentation forms A
            c in 'ﹰ'..'﻿'     // Arabic presentation forms B

    /** A letter that carries a direction of its own. Everything else is neutral. */
    private fun isStrongChar(c: Char): Boolean = isRtlChar(c) || c.isLetter()

    /**
     * Whether [line] reads right-to-left, or null when it has no strong letter to judge by
     * (blank, digits, punctuation only).
     */
    fun isRtlLine(line: String): Boolean? {
        for (c in line) {
            if (isStrongChar(c)) {
                return isRtlChar(c)
            }
        }
        return null
    }

    /** One line of the source: its character range, and the direction it reads in. */
    data class Line(val start: Int, val end: Int, val rtl: Boolean)

    /**
     * [text] split into lines with a direction each, ranges indexing back into [text].
     *
     * The newline is kept inside its line's range so the ranges tile the whole string without a
     * gap — a paragraph style applied to a gapped range leaves the newline unstyled and Compose
     * then renders an extra, differently-aligned empty paragraph.
     */
    fun lines(text: String, fallbackRtl: Boolean = false): List<Line> {
        val out = mutableListOf<Line>()
        var start = 0
        var previous = fallbackRtl
        var i = 0
        while (i <= text.length) {
            if (i == text.length || text[i] == '\n') {
                val end = if (i == text.length) i else i + 1
                if (end > start || i == text.length) {
                    val rtl = isRtlLine(text.substring(start, minOf(i, text.length))) ?: previous
                    previous = rtl
                    out.add(Line(start, end, rtl))
                }
                start = i + 1
            }
            i++
        }
        return out
    }

    /**
     * A paragraph style for one line: its own direction, and justified.
     *
     * Justification stretches every line of a paragraph except its last, so on the one-line
     * paragraphs this produces it changes nothing visually — the alignment that matters is the
     * direction-derived start edge. It earns its place on a long wrapped line, which is where the
     * ragged edge was showing.
     */
    fun paragraphStyle(rtl: Boolean): ParagraphStyle = ParagraphStyle(
        textDirection = if (rtl) TextDirection.Rtl else TextDirection.Ltr,
        textAlign = TextAlign.Justify,
    )

    /** [text] with each line carrying its own direction, justified. */
    fun directional(text: String, fallbackRtl: Boolean = false): AnnotatedString =
        buildAnnotatedString {
            append(text)
            lines(text, fallbackRtl).forEach { addStyle(paragraphStyle(it.rtl), it.start, it.end) }
        }
}
