import { useMemo } from 'react';
import { Box } from '@mui/material';
import MarkdownIt from 'markdown-it';
import markdownItMark from 'markdown-it-mark';

// html:true keeps legacy <br>/HTML working during the move to Markdown; breaks:true turns
// plain newlines into line breaks; markdown-it-mark renders ==text== as <mark>.
const md = new MarkdownIt({ html: true, linkify: true, breaks: true }).use(markdownItMark);

// Every link — explicit [text](url) markdown or a bare URL auto-linkified by linkify:true — opens
// in a new tab, same as the dedicated "url"-type field in DetailPane. noopener/noreferrer since
// the opened page would otherwise get a handle back to this window via window.opener.
const defaultLinkOpen =
  md.renderer.rules.link_open ??
  ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options));
md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  tokens[idx].attrSet('target', '_blank');
  tokens[idx].attrSet('rel', 'noopener noreferrer');
  return defaultLinkOpen(tokens, idx, options, env, self);
};

/**
 * Rewrite `- [ ]` / `- [x]` lines into list items carrying a real checkbox.
 *
 * Done before markdown-it rather than with a plugin so the app keeps its dependency set, and done
 * as a line rewrite rather than a renderer rule so the surrounding list is still parsed as a list.
 * Fenced code blocks are skipped for the same reason `checklistItems` skips them — a `- [ ]` inside
 * a fence is documentation of the syntax (the cheatsheet renders exactly that), and turning it into
 * a control would both look wrong and shift every index after it.
 */
const ITEM = /^(\s*)([-*+])(\s+)\[([ xX])\](\s+)(.*)$/;
const FENCE = /^\s*(```|~~~)/;

function withCheckboxes(source: string, interactive: boolean): string {
  let inFence = false;
  let index = 0;
  return source
    .split('\n')
    .map((line) => {
      if (FENCE.test(line)) {
        inFence = !inFence;
        return line;
      }
      if (inFence) return line;
      const m = ITEM.exec(line);
      if (!m) return line;
      const [, indent, bullet, s1, box, , text] = m;
      const checked = box !== ' ' ? ' checked' : '';
      const disabled = interactive ? '' : ' disabled';
      const cb =
        `<input type="checkbox" class="md-task"${checked}${disabled} data-cb="${index++}">`;
      return `${indent}${bullet}${s1}${cb} <span class="md-task-text">${text}</span>`;
    })
    .join('\n');
}

/**
 * Renders a text field as Markdown (with ==highlight== support).
 *
 * `onToggleItem` makes checklist items tickable: it receives the item's index among the checklist
 * items of the text, which is the same numbering `toggleChecklistItem` rewrites by. Without it the
 * boxes still render — a checklist should look like one wherever it is shown — but disabled.
 */
export function MarkdownView({
  value,
  onToggleItem,
}: {
  value: string;
  onToggleItem?: (index: number) => void;
}) {
  const html = useMemo(
    () => md.render(withCheckboxes(value ?? '', Boolean(onToggleItem))),
    [value, onToggleItem],
  );
  return (
    <Box
      dir="auto"
      onClick={(e) => {
        if (!onToggleItem) return;
        const el = (e.target as HTMLElement).closest('input.md-task') as HTMLInputElement | null;
        if (!el) return;
        // The DOM checkbox is a view of the text, never the source of truth: let the record change
        // and re-render decide. Otherwise a failed save would leave a ticked box over untouched text.
        e.preventDefault();
        const index = Number(el.dataset.cb);
        if (Number.isFinite(index)) onToggleItem(index);
      }}
      sx={{
        lineHeight: 1.7,
        '& img': { maxWidth: '100%' },
        '& mark': { backgroundColor: '#fff3a3', px: '2px', borderRadius: '2px' },
        '& pre': { bgcolor: '#f5f5f5', p: 1.5, borderRadius: 1, overflow: 'auto' },
        '& code': { bgcolor: '#f5f5f5', px: '4px', borderRadius: '3px' },
        '& blockquote': { borderLeft: '3px solid #ccc', pl: 1.5, color: 'text.secondary', my: 1 },
        '& :first-of-type': { mt: 0 },
        // A task list reads as a list of things to do, not as bullets that happen to have boxes.
        '& input.md-task': {
          mr: 0.75,
          verticalAlign: 'middle',
          width: 16,
          height: 16,
          cursor: onToggleItem ? 'pointer' : 'default',
          accentColor: '#0078d4',
        },
        '& input.md-task:checked + .md-task-text': { opacity: 0.55, textDecoration: 'line-through' },
      }}
      dangerouslySetInnerHTML={{ __html: html }}
    />
  );
}
