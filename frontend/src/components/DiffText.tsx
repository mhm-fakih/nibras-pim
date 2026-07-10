import { Box, Typography } from '@mui/material';

type Seg = { type: 'same' | 'add' | 'del'; text: string };

/** Word-level diff via LCS (whitespace kept as tokens so spacing is preserved). */
function wordDiff(a: string, b: string): Seg[] {
  const aw = (a ?? '').split(/(\s+)/);
  const bw = (b ?? '').split(/(\s+)/);
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

/** Side-by-side diff: the old value (deletions red) on the left, the new value (additions green) on the right. */
export function DiffText({ oldValue, newValue }: { oldValue?: string; newValue?: string }) {
  const segs = wordDiff(oldValue ?? '', newValue ?? '');

  const column = (kind: 'del' | 'add', label: string) => (
    <Box sx={{ flex: 1, minWidth: 0 }}>
      <Typography variant="caption" sx={{ color: 'text.disabled', display: 'block' }}>
        {label}
      </Typography>
      <Box
        dir="auto"
        sx={{ whiteSpace: 'pre-wrap', fontSize: 13, lineHeight: 1.6, maxHeight: 220, overflow: 'auto' }}
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
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'stretch' }}>
      {column('del', 'before')}
      <Box sx={{ width: '1px', bgcolor: 'divider', flexShrink: 0 }} />
      {column('add', 'after')}
    </Box>
  );
}
