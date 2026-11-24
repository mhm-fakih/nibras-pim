import { useMemo } from 'react';
import { Box, Button, CircularProgress, Stack, Typography } from '@mui/material';
import { useHighlightStyle } from '../hooks/useHighlightStyle';
import { isRtlText } from '../util/rtl';
import { sentenceSpans, splitMarks } from '../util/highlights';

/**
 * Click-to-highlight body text, matching the phone and the desktop: clicking a sentence wraps it
 * in `==…==` in the source field (and the page adds it to the record's highlights field); clicking
 * a highlighted sentence clears it.
 *
 * Whole sentences, split on the rules in `util/highlights.ts` — device-independent, so a highlight
 * stores the same text whatever the screen. Highlights are PAINTED from the per-character flags,
 * not matched sentence by sentence, so an older clause-sized highlight still shows exactly where it
 * was; clicking its sentence folds it into one sentence highlight.
 */
export function HighlightableText({
  source,
  onToggle,
  disabled,
  reading,
}: {
  /** The raw field value, including any existing `==…==` markers. */
  source: string;
  /** Called with the clicked sentence's range in the marker-free text. */
  onToggle: (start: number, end: number) => void;
  disabled?: boolean;
  /** Long-form reading (a resource's full text): justified, the font scaled by this. */
  reading?: number;
}) {
  const highlightStyle = useHighlightStyle();
  const { plain, flags } = useMemo(() => splitMarks(source), [source]);
  const rtl = isRtlText(plain);
  const sentences = useMemo(() => sentenceSpans(plain), [plain]);

  /**
   * The sentences with the ORIGINAL text between them put back — a space, a newline, a blank line
   * is emitted verbatim, so paragraphs survive `pre-wrap` — and each sentence cut into runs of
   * the same highlight state.
   */
  const pieces = useMemo(() => {
    const out: Array<
      | { kind: 'gap'; text: string }
      | { kind: 'sentence'; start: number; end: number; runs: Array<{ text: string; on: boolean }> }
    > = [];
    let pos = 0;
    for (const s of sentences) {
      if (s.start > pos) out.push({ kind: 'gap', text: plain.slice(pos, s.start) });
      const runs: Array<{ text: string; on: boolean }> = [];
      for (let i = s.start; i < s.end; i++) {
        const last = runs[runs.length - 1];
        if (last && last.on === flags[i]) last.text += plain[i];
        else runs.push({ text: plain[i], on: flags[i] });
      }
      out.push({ kind: 'sentence', start: s.start, end: s.end, runs });
      pos = s.end;
    }
    if (pos < plain.length) out.push({ kind: 'gap', text: plain.slice(pos) });
    return out;
  }, [plain, flags, sentences]);

  return (
    <Box
      dir={rtl ? 'rtl' : 'ltr'}
      sx={{
        textAlign: reading ? 'justify' : rtl ? 'right' : 'left',
        textAlignLast: rtl ? 'right' : 'left',
        whiteSpace: 'pre-wrap',
        lineHeight: 1.9,
        ...(reading && { fontSize: `${reading}em` }),
      }}
    >
      {pieces.map((p, i) => {
        if (p.kind === 'gap') return <span key={i}>{p.text}</span>;
        return (
          <Box
            component="span"
            key={i}
            onClick={disabled ? undefined : () => onToggle(p.start, p.end)}
            sx={{
              borderRadius: 0.5,
              cursor: disabled ? 'default' : 'pointer',
              '&:hover': disabled ? undefined : { bgcolor: 'action.hover' },
            }}
          >
            {p.runs.map((r, k) =>
              r.on ? (
                <Box component="span" key={k} sx={highlightStyle}>
                  {r.text}
                </Box>
              ) : (
                <span key={k}>{r.text}</span>
              ),
            )}
          </Box>
        );
      })}
    </Box>
  );
}

/** The Highlight button above a record's fields, and what clicking does while the mode is on. */
export function HighlightBar({
  highlighting,
  onChange,
  pending,
  targetLabel,
}: {
  highlighting: boolean;
  onChange: (on: boolean) => void;
  pending: boolean;
  /** The label of the field highlighted sentences are collected in. */
  targetLabel?: string;
}) {
  return (
    <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap sx={{ mb: 1 }}>
      <Button
        size="small"
        variant={highlighting ? 'contained' : 'outlined'}
        color="warning"
        onClick={() => onChange(!highlighting)}
      >
        {highlighting ? 'Done highlighting' : 'Highlight'}
      </Button>
      {highlighting && (
        <Typography variant="caption" color="text.secondary">
          Click a sentence to highlight it — it also goes to “{targetLabel}”. Click it again to clear.
        </Typography>
      )}
      {pending && <CircularProgress size={16} />}
    </Stack>
  );
}
