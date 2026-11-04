/**
 * `==…==` highlight markers and sentence boundaries — the web twin of the phone's
 * `util/Highlights.kt` + `util/Clauses.kt` and the desktop's `notes.py`. Keep the three in step:
 * a highlight is stored as text, so if the clients disagree on where a sentence ends or how a
 * marker pairs up, the same click saves different text — or leaves a stray `=` behind.
 *
 * The model: a body is read as plain text plus a per-character "highlighted" flag; a click flips
 * the flags of one sentence; the markers are written back from the flags. Markers are never
 * spliced into the source by searching for a snippet (that nested them — `==A, ==b,== c.==` —
 * when a sentence already held an older clause highlight). And before anything is saved, the
 * result is read back: if the text a reader would see differs by one character, nothing is saved.
 */

export interface Span {
  start: number;
  end: number;
  text: string;
}

const MARK = '==';

/** Sentence terminators — Arabic `؟`/`؛` included, plus the line break. */
const SENTENCE_BREAKS = '.!?…؟؛\n';

const isWordChar = (c: string | undefined) => !!c && /[\p{L}\p{N}_]/u.test(c);
const isSpace = (c: string) => /\s/.test(c);

/**
 * Whether `plain[i]` ends a sentence: `. ! ? … ؟ ؛` and the line break do; a `.` between two word
 * characters doesn't (`3.14`, `notes.md`, a URL); a run of terminators (`?!`, `...`) ends it once,
 * after the last of them.
 */
function endsSentence(plain: string, i: number): boolean {
  const ch = plain[i];
  if (!SENTENCE_BREAKS.includes(ch)) return false;
  if (ch === '\n') return true;
  const next = plain[i + 1];
  if (next !== undefined && SENTENCE_BREAKS.includes(next)) return false;
  if (ch === '.' && isWordChar(plain[i - 1]) && isWordChar(next)) return false;
  return true;
}

function trimmed(plain: string, from: number, to: number): Span | null {
  let s = from;
  let e = to;
  while (s < e && isSpace(plain[s])) s++;
  while (e > s && isSpace(plain[e - 1])) e--;
  return s < e ? { start: s, end: e, text: plain.slice(s, e) } : null;
}

/** Sentences of the marker-free text as trimmed ranges, offsets preserved. */
export function sentenceSpans(plain: string): Span[] {
  const out: Span[] = [];
  let start = 0;
  for (let i = 0; i < plain.length; i++) {
    if (endsSentence(plain, i)) {
      const t = trimmed(plain, start, i + 1);
      if (t) out.push(t);
      start = i + 1;
    }
  }
  const t = trimmed(plain, start, plain.length);
  if (t) out.push(t);
  return out;
}

/**
 * Plain text and per-character flags. A `==` opens a highlight only if another `==` follows with
 * at least one character between; an unpaired one is literal text.
 */
export function splitMarks(source: string): { plain: string; flags: boolean[] } {
  let plain = '';
  const flags: boolean[] = [];
  let i = 0;
  while (i < source.length) {
    if (source.startsWith(MARK, i)) {
      const close = source.indexOf(MARK, i + MARK.length + 1);
      if (close !== -1) {
        const body = source.slice(i + MARK.length, close);
        plain += body;
        for (let k = 0; k < body.length; k++) flags.push(true);
        i = close + MARK.length;
        continue;
      }
    }
    plain += source[i];
    flags.push(false);
    i++;
  }
  return { plain, flags };
}

/** Markers written back around each highlighted run; edge whitespace stays outside them. */
export function joinMarks(plain: string, flags: boolean[]): string {
  let out = '';
  let i = 0;
  const n = plain.length;
  while (i < n) {
    if (!flags[i]) {
      out += plain[i++];
      continue;
    }
    let runEnd = i;
    while (runEnd < n && flags[runEnd]) runEnd++;
    let s = i;
    let e = runEnd;
    while (s < e && isSpace(plain[s])) out += plain[s++];
    while (e > s && isSpace(plain[e - 1])) e--;
    if (s < e) out += MARK + plain.slice(s, e) + MARK;
    out += plain.slice(e, runEnd);
    i = runEnd;
  }
  return out;
}

/** Highlighted runs as trimmed ranges. */
export function markedRuns(plain: string, flags: boolean[]): Span[] {
  const out: Span[] = [];
  let i = 0;
  while (i < plain.length) {
    if (!flags[i]) {
      i++;
      continue;
    }
    let e = i;
    while (e < plain.length && flags[e]) e++;
    const t = trimmed(plain, i, e);
    if (t) out.push(t);
    i = e;
  }
  return out;
}

export function stripMarks(source: string): string {
  return splitMarks(source).plain;
}

export interface ToggleResult {
  /** The new source. */
  source: string;
  /** Highlight texts that no longer exist (to drop from a highlights collector). */
  removed: string[];
  /** Highlight texts that are new (to append to it). */
  added: string[];
}

/**
 * Flip the highlight on `plain[start, end)`. Fully highlighted (blanks aside) → cleared; anything
 * less → the whole range highlighted, absorbing any older, smaller highlight inside it. Returns
 * null when the range is empty or the result wouldn't read back as exactly the same text.
 */
export function toggleRange(source: string, start: number, end: number): ToggleResult | null {
  const { plain, flags } = splitMarks(source);
  if (start < 0 || end > plain.length || start >= end) return null;
  const before = flags.slice();
  let on = false;
  for (let i = start; i < end; i++) if (!flags[i] && !isSpace(plain[i])) on = true;
  for (let i = start; i < end; i++) flags[i] = on;
  const updated = joinMarks(plain, flags);

  // The guard: same reading text, intended state inside the range, unchanged state outside.
  const again = splitMarks(updated);
  if (again.plain !== plain) return null;
  for (let i = 0; i < plain.length; i++) {
    if (isSpace(plain[i])) continue;
    const want = i >= start && i < end ? on : before[i];
    if (again.flags[i] !== want) return null;
  }

  const key = (r: Span) => `${r.start}:${r.end}`;
  const oldRuns = markedRuns(plain, before);
  const newRuns = markedRuns(plain, again.flags);
  const oldKeys = new Set(oldRuns.map(key));
  const newKeys = new Set(newRuns.map(key));
  return {
    source: updated,
    removed: oldRuns.filter((r) => !newKeys.has(key(r))).map((r) => r.text),
    added: newRuns.filter((r) => !oldKeys.has(key(r))).map((r) => r.text),
  };
}

/** Append to a highlights field the way the backend's sync append() does (blank-line separated). */
export function appendHighlight(current: string | null | undefined, text: string): string {
  return !current || !current.trim() ? text : `${current}\n\n${text}`;
}

/** Drop one blank-line-separated entry from a highlights field. */
export function removeHighlight(current: string | null | undefined, text: string): string {
  if (!current) return '';
  return current
    .split(/\n{2,}/)
    .map((s) => s.trim())
    .filter((s) => s && s !== text.trim())
    .join('\n\n');
}
