import { useMemo } from 'react';
import { Box } from '@mui/material';
import { isRtlText } from '../util/rtl';

/**
 * Click-to-highlight body text, mirroring the mobile resource card: tapping a chunk wraps it in
 * `==…==` in the source field and appends its text to the record's highlights field; tapping an
 * already-highlighted chunk removes both.
 *
 * Two granularities, because articles differ — both device-independent, so a highlight stores the
 * same text whatever the screen (a visual-line split would not: it changes with viewport width,
 * font size and zoom, so the same click would store different text on the phone than the desktop):
 * - `sentence` — grammatical sentences, split on the same terminators the mobile app uses
 *   (`. ! ? ؟ ؛` and line breaks). Matches what the phone stores.
 * - `clause` — sentences further split at `، ؛ , ; : —` and dashes, for articles whose sentences
 *   run for a whole paragraph and would otherwise highlight in one huge block.
 */
export type HighlightMode = 'sentence' | 'clause';

/** A chunk of the source text as a character range, with the marker-free text the record stores. */
export interface Chunk {
  /** Text as it appears to the reader (no `==` markers). */
  text: string;
  /** Whether this chunk is currently wrapped in `==…==` in the source. */
  marked: boolean;
}

const MARK = /==([\s\S]+?)==/g;

/** Strip `==…==` markers, remembering which ranges were marked. */
export function stripMarkers(source: string): { plain: string; markedTexts: string[] } {
  const markedTexts: string[] = [];
  const plain = source.replace(MARK, (_m, inner: string) => {
    markedTexts.push(inner.trim());
    return inner;
  });
  return { plain, markedTexts };
}

/**
 * Sentence spans over the marker-free text. Same terminator set as the mobile splitter so a
 * highlight made on either client lands on the same boundaries.
 */
export function splitSentences(plain: string): string[] {
  const out: string[] = [];
  let buf = '';
  for (const ch of plain) {
    buf += ch;
    if ('.!?؟؛\n'.includes(ch)) {
      if (buf.trim()) out.push(buf.trim());
      buf = '';
    }
  }
  if (buf.trim()) out.push(buf.trim());
  return out;
}

/** Clause terminators — Arabic comma/semicolon included, plus the dashes used as asides. */
const CLAUSE_BREAKS = '،؛,;:—–';

/** Sentences, then each split again at clause boundaries. Keeps the break character with its clause. */
export function splitClauses(plain: string): string[] {
  const out: string[] = [];
  for (const sentence of splitSentences(plain)) {
    let buf = '';
    for (const ch of sentence) {
      buf += ch;
      if (CLAUSE_BREAKS.includes(ch)) {
        if (buf.trim()) out.push(buf.trim());
        buf = '';
      }
    }
    if (buf.trim()) out.push(buf.trim());
  }
  return out;
}

export function splitChunks(plain: string, mode: HighlightMode): string[] {
  return mode === 'clause' ? splitClauses(plain) : splitSentences(plain);
}

/** Wrap the first unmarked occurrence of `snippet` in `==…==`. Returns the source unchanged if absent. */
export function addMarker(source: string, snippet: string): string {
  const i = source.indexOf(snippet);
  if (i < 0) return source;
  // Already wrapped right here? leave it alone.
  if (source.slice(Math.max(0, i - 2), i) === '==') return source;
  return source.slice(0, i) + '==' + snippet + '==' + source.slice(i + snippet.length);
}

/** Remove the `==…==` wrapper around `snippet`. */
export function removeMarker(source: string, snippet: string): string {
  return source.replace(`==${snippet}==`, snippet);
}

/** Append to the highlights field the same way the backend's sync append() does (blank-line separated). */
export function appendHighlight(current: string | null | undefined, text: string): string {
  return !current || !current.trim() ? text : `${current}\n\n${text}`;
}

/** Drop one blank-line-separated entry from the highlights field. */
export function removeHighlight(current: string | null | undefined, text: string): string {
  if (!current) return '';
  return current
    .split(/\n{2,}/)
    .map((s) => s.trim())
    .filter((s) => s && s !== text.trim())
    .join('\n\n');
}

export function HighlightableText({
  source,
  mode,
  onToggle,
  disabled,
}: {
  /** The raw field value, including any existing `==…==` markers. */
  source: string;
  mode: HighlightMode;
  /** Called with the reader-visible text of the clicked chunk and whether it was already marked. */
  onToggle: (text: string, wasMarked: boolean) => void;
  disabled?: boolean;
}) {
  const { plain, markedTexts } = useMemo(() => stripMarkers(source), [source]);
  const rtl = isRtlText(plain);
  const isMarked = useMemo(() => {
    const set = new Set(markedTexts);
    return (t: string) => set.has(t.trim());
  }, [markedTexts]);
  const chunks = useMemo(() => splitChunks(plain, mode), [plain, mode]);

  /**
   * The chunks with the ORIGINAL text between them put back.
   *
   * The splitters trim each chunk, which is right — a highlight has to match the same string
   * whichever client made it, and trailing whitespace would break that. But it means the newlines
   * are gone by the time anything is rendered, and joining the chunks with a space (as this used
   * to) turned every paragraph break into a space: the whole record read as one run-on line, with
   * `pre-wrap` set on a container that had no newlines left to honour.
   *
   * So the gaps are recovered from `plain` by position rather than reconstructed. Whatever was
   * between two chunks — a space, a newline, a blank line — is emitted verbatim, and the chunks
   * stay trimmed for matching.
   */
  const pieces = useMemo(() => {
    const out: Array<{ text: string; chunk: boolean }> = [];
    let pos = 0;
    for (const c of chunks) {
      const i = plain.indexOf(c, pos);
      if (i < 0) {
        out.push({ text: c, chunk: true });   // shouldn't happen; render it rather than drop it
        continue;
      }
      if (i > pos) out.push({ text: plain.slice(pos, i), chunk: false });
      out.push({ text: c, chunk: true });
      pos = i + c.length;
    }
    if (pos < plain.length) out.push({ text: plain.slice(pos), chunk: false });
    return out;
  }, [plain, chunks]);

  return (
    <Box
      dir={rtl ? 'rtl' : 'ltr'}
      sx={{ textAlign: rtl ? 'right' : 'left', whiteSpace: 'pre-wrap', lineHeight: 1.9 }}
    >
      {pieces.map((p, i) => {
        if (!p.chunk) {
          return <span key={i}>{p.text}</span>;
        }
        const marked = isMarked(p.text);
        return (
          <Box
            component="span"
            key={i}
            onClick={disabled ? undefined : () => onToggle(p.text, marked)}
            sx={{
              bgcolor: marked ? 'warning.light' : undefined,
              borderRadius: 0.5,
              cursor: disabled ? 'default' : 'pointer',
              '&:hover': disabled ? undefined : { bgcolor: marked ? 'warning.main' : 'action.hover' },
            }}
          >
            {p.text}
          </Box>
        );
      })}
    </Box>
  );
}
