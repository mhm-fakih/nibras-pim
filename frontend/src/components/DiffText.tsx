import { Box, Typography } from '@mui/material';
import { Fragment } from 'react';

type Seg = { type: 'same' | 'add' | 'del'; text: string };

/** LCS diff of two token lists. */
function diffTokens(aw: string[], bw: string[]): Seg[] {
  const n = aw.length;
  const m = bw.length;
  const dp: number[][] = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0));
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      dp[i][j] = aw[i] === bw[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
    }
  }
  const out: Seg[] = [];
  let i = 0;
  let j = 0;
  while (i < n && j < m) {
    if (aw[i] === bw[j]) {
      out.push({ type: 'same', text: aw[i] });
      i++;
      j++;
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      out.push({ type: 'del', text: aw[i++] });
    } else {
      out.push({ type: 'add', text: bw[j++] });
    }
  }
  while (i < n) out.push({ type: 'del', text: aw[i++] });
  while (j < m) out.push({ type: 'add', text: bw[j++] });
  return out;
}

/** Word-level diff (whitespace kept as tokens so spacing is preserved). */
function wordDiff(a: string, b: string): Seg[] {
  return diffTokens((a ?? '').split(/(\s+)/), (b ?? '').split(/(\s+)/));
}

/** Unchanged lines kept around each change, so it can be found in the file. */
const CONTEXT_LINES = 1;

/**
 * The changed stretches of the two texts, each as its before/after text with [CONTEXT_LINES] of
 * unchanged lines around it. Changes closer than twice that share one hunk.
 */
function hunks(a: string, b: string): { before: string; after: string }[] {
  const al = a.split('\n');
  const bl = b.split('\n');
  // Strip the common head and tail first: a description edited in one place is otherwise a full
  // line-by-line LCS over the whole document.
  let head = 0;
  while (head < al.length && head < bl.length && al[head] === bl[head]) head++;
  let tail = 0;
  while (
    tail < al.length - head && tail < bl.length - head &&
    al[al.length - 1 - tail] === bl[bl.length - 1 - tail]
  ) tail++;
  const mid = diffTokens(al.slice(head, al.length - tail), bl.slice(head, bl.length - tail));
  const lines: Seg[] = [
    ...al.slice(0, head).map((text) => ({ type: 'same' as const, text })),
    ...mid,
    ...al.slice(al.length - tail).map((text) => ({ type: 'same' as const, text })),
  ];

  // Which lines to show: every changed line and its context.
  const keep = lines.map(() => false);
  lines.forEach((l, i) => {
    if (l.type === 'same') return;
    for (let k = i - CONTEXT_LINES; k <= i + CONTEXT_LINES; k++) {
      if (k >= 0 && k < lines.length) keep[k] = true;
    }
  });

  const out: { before: string; after: string }[] = [];
  let cur: { before: string[]; after: string[] } | null = null;
  lines.forEach((l, i) => {
    if (!keep[i]) {
      if (cur) out.push({ before: cur.before.join('\n'), after: cur.after.join('\n') });
      cur = null;
      return;
    }
    cur ??= { before: [], after: [] };
    if (l.type !== 'add') cur.before.push(l.text);
    if (l.type !== 'del') cur.after.push(l.text);
  });
  if (cur) {
    const c = cur as { before: string[]; after: string[] };
    out.push({ before: c.before.join('\n'), after: c.after.join('\n') });
  }
  return out;
}

/**
 * Side-by-side diff: the old value (deletions red) on the left, the new value (additions green) on
 * the right — only the changed lines, with a line of context, rather than both texts in full: in a
 * long description the one edited sentence was lost in a scroll of unchanged text.
 */
export function DiffText({ oldValue, newValue }: { oldValue?: string; newValue?: string }) {
  const parts = hunks(oldValue ?? '', newValue ?? '');

  const column = (segs: Seg[], kind: 'del' | 'add') => (
    <Box
      dir="auto"
      sx={{ flex: 1, minWidth: 0, whiteSpace: 'pre-wrap', fontSize: 13, lineHeight: 1.6 }}
    >
      {segs
        .filter((s) => s.type === 'same' || s.type === kind)
        .map((s, i) =>
          s.type === 'same' || /^\s+$/.test(s.text) ? (
            <span key={i}>{s.text}</span>
          ) : (
            <Box
              key={i}
              component={kind === 'add' ? 'ins' : 'del'}
              sx={{
                bgcolor: kind === 'add' ? '#c8e6c9' : '#ffcdd2',
                textDecoration: kind === 'add' ? 'none' : 'line-through',
                borderRadius: 0.5,
                px: 0.25,
              }}
            >
              {s.text}
            </Box>
          ),
        )}
    </Box>
  );

  const label = (text: string) => (
    <Typography variant="caption" sx={{ flex: 1, color: 'text.disabled' }}>
      {text}
    </Typography>
  );

  return (
    <Box sx={{ maxHeight: 320, overflow: 'auto' }}>
      <Box sx={{ display: 'flex', gap: 1.5 }}>
        {label('before')}
        <Box sx={{ width: '1px', flexShrink: 0 }} />
        {label('after')}
      </Box>
      {parts.map((p, i) => {
        const segs = wordDiff(p.before, p.after);
        return (
          <Fragment key={i}>
            {i > 0 && (
              <Typography variant="caption" sx={{ display: 'block', color: 'text.disabled', textAlign: 'center' }}>
                ⋯
              </Typography>
            )}
            <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'stretch' }}>
              {column(segs, 'del')}
              <Box sx={{ width: '1px', bgcolor: 'divider', flexShrink: 0 }} />
              {column(segs, 'add')}
            </Box>
          </Fragment>
        );
      })}
    </Box>
  );
}
