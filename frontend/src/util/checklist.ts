/**
 * Markdown task lists (`- [ ]` / `- [x]`) as data.
 *
 * The parsing lives here rather than in the renderer because the *same* line numbering has to be
 * agreed on by two places that never see each other's work: the renderer, which turns the nth item
 * into the nth checkbox, and the toggle, which rewrites the nth item in the stored text. Anything
 * that changed the count on one side only — a checkbox inside a fenced code block, say — would tick
 * one box and flip a different line.
 */

/** `- [ ] text`, `* [x] text`, `+ [X] text`, at any indent. */
const ITEM = /^(\s*)([-*+])(\s+)\[([ xX])\](\s+)(.*)$/;

/** A fence opens or closes a code block: ``` or ~~~, at any indent. */
const FENCE = /^\s*(```|~~~)/;

export interface ChecklistItem {
  /** 0-based position among the checklist items of the text, in reading order. */
  index: number;
  /** Line number in the source (0-based), for the rewrite. */
  line: number;
  checked: boolean;
  text: string;
}

/**
 * Every checklist item in `source`, skipping fenced code blocks.
 *
 * Code fences are excluded deliberately: a `- [ ]` inside a fence is *documentation of the syntax*,
 * not a task. Counting it would shift every index after it — and the cheatsheet page renders
 * exactly that, so this is a case that occurs in practice rather than a hypothetical one.
 */
export function checklistItems(source: string): ChecklistItem[] {
  const out: ChecklistItem[] = [];
  let inFence = false;
  source.split('\n').forEach((line, n) => {
    if (FENCE.test(line)) {
      inFence = !inFence;
      return;
    }
    if (inFence) return;
    const m = ITEM.exec(line);
    if (m) out.push({ index: out.length, line: n, checked: m[4] !== ' ', text: m[6] });
  });
  return out;
}

/** Whether `source` has anything to tick — cheap enough to call on every render. */
export function hasChecklist(source: string): boolean {
  return checklistItems(source).length > 0;
}

/**
 * `source` with item `index` flipped, everything else byte-for-byte unchanged.
 *
 * Only the single character between the brackets is rewritten — not the line, not the list — so a
 * tick cannot reflow the author's spacing, bullet character or indentation. That matters because
 * this text is also edited by hand, in the checkout `.md` and the desktop editor, and a renderer
 * that quietly reformatted on every click would fight whoever wrote it.
 *
 * Returns `source` unchanged when there is no such item.
 */
export function toggleChecklistItem(source: string, index: number): string {
  const item = checklistItems(source)[index];
  if (!item) return source;
  const lines = source.split('\n');
  lines[item.line] = lines[item.line].replace(ITEM, (_full, indent, bullet, s1, _box, s2, text) =>
    `${indent}${bullet}${s1}[${item.checked ? ' ' : 'x'}]${s2}${text}`,
  );
  return lines.join('\n');
}

/** How many items are ticked, out of how many — the score of a routine's run. */
export function checklistProgress(source: string): { done: number; total: number } {
  const items = checklistItems(source);
  return { done: items.filter((i) => i.checked).length, total: items.length };
}
