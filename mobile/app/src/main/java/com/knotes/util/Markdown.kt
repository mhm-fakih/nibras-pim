package com.knotes.util

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em

/**
 * The small slice of Markdown a note actually uses, as styles over the text.
 *
 * <h3>Styled in place, not rewritten</h3>
 * The markers stay in the string and are *dimmed* rather than deleted. That is deliberate and it is
 * what makes this safe to bolt onto the reader: every character keeps its original offset, so the
 * clause spans, the tap-to-highlight hit-testing and the `==…==` highlight ranges all still line up
 * with the text they were computed from. Stripping the markers would shift every position after the
 * first `**` and quietly highlight the wrong words.
 *
 * <h3>What it covers</h3>
 * `**bold**`, `*italic*` / `_italic_`, `` `code` ``, `~~strike~~`, and `#` headings — the things
 * that turn up in a captured note. Not a Markdown implementation: no links, lists, tables or block
 * quotes, because a note reader that half-parses those is worse than one that leaves them as text.
 */
object Markdown {

    /** A styled range within the source text. */
    data class Span(val start: Int, val end: Int, val style: SpanStyle)

    private val BOLD = Regex("""\*\*(?=\S)(.+?)(?<=\S)\*\*""", RegexOption.DOT_MATCHES_ALL)
    private val ITALIC_STAR = Regex("""(?<!\*)\*(?!\*)(?=\S)(.+?)(?<=\S)\*(?!\*)""")
    private val ITALIC_UNDER = Regex("""(?<![\w_])_(?=\S)(.+?)(?<=\S)_(?![\w_])""")
    private val CODE = Regex("""`([^`\n]+)`""")
    private val STRIKE = Regex("""~~(?=\S)(.+?)(?<=\S)~~""")
    private val HEADING = Regex("""^(#{1,6})\s+(.*)$""", RegexOption.MULTILINE)

    /** The marker characters themselves: present, but faded so the text reads cleanly. */
    private val MARKER = SpanStyle(fontWeight = FontWeight.Light)

    /**
     * Styles for [text], including one over each marker so it can be dimmed by the caller.
     *
     * Emphasis inside a code span is left alone — backticks mean "as written", and a path holding
     * an underscore is not italic.
     */
    fun spans(text: String): List<Span> {
        val out = mutableListOf<Span>()
        val code = CODE.findAll(text).toList()
        val inCode = { i: Int -> code.any { i >= it.range.first && i <= it.range.last } }

        HEADING.findAll(text).forEach { m ->
            val level = m.groupValues[1].length
            out += Span(
                m.range.first, m.range.last + 1,
                SpanStyle(fontWeight = FontWeight.Bold, fontSize = headingScale(level)),
            )
        }
        BOLD.findAll(text).filterNot { inCode(it.range.first) }.forEach { m ->
            out += Span(m.range.first, m.range.last + 1, SpanStyle(fontWeight = FontWeight.Bold))
            out += markerSpans(m.range.first, m.range.last + 1, 2)
        }
        ITALIC_STAR.findAll(text).filterNot { inCode(it.range.first) }.forEach { m ->
            out += Span(m.range.first, m.range.last + 1, SpanStyle(fontStyle = FontStyle.Italic))
            out += markerSpans(m.range.first, m.range.last + 1, 1)
        }
        ITALIC_UNDER.findAll(text).filterNot { inCode(it.range.first) }.forEach { m ->
            out += Span(m.range.first, m.range.last + 1, SpanStyle(fontStyle = FontStyle.Italic))
            out += markerSpans(m.range.first, m.range.last + 1, 1)
        }
        STRIKE.findAll(text).filterNot { inCode(it.range.first) }.forEach { m ->
            out += Span(m.range.first, m.range.last + 1, SpanStyle(textDecoration = TextDecoration.LineThrough))
            out += markerSpans(m.range.first, m.range.last + 1, 2)
        }
        code.forEach { m ->
            out += Span(
                m.range.first, m.range.last + 1,
                SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
            )
        }
        return out
    }

    /** Both delimiters of a run, so the caller can fade them. */
    private fun markerSpans(start: Int, end: Int, width: Int): List<Span> =
        listOf(Span(start, start + width, MARKER), Span(end - width, end, MARKER))

    /**
     * Heading size as a multiple of the surrounding text, not an absolute point size — the reader's
     * body size is the user's, so a fixed `sp` here would fight it.
     */
    private fun headingScale(level: Int): TextUnit = when (level) {
        1 -> 1.4.em
        2 -> 1.25.em
        3 -> 1.12.em
        else -> 1.05.em
    }
}
