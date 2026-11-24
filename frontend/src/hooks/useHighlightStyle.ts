import { useTheme } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { getSetting } from '../api/client';

/** Setting key: the background of highlighted text, as #rrggbb. */
export const HIGHLIGHT_COLOR_SETTING = 'highlight.color';
export const DEFAULT_HIGHLIGHT_COLOR = '#e3f7df';

/** The configured highlight colour; the default if unset or not a #rrggbb value. */
export function useHighlightColor(): string {
  const { data } = useQuery({
    queryKey: ['setting', HIGHLIGHT_COLOR_SETTING],
    queryFn: () => getSetting(HIGHLIGHT_COLOR_SETTING, DEFAULT_HIGHLIGHT_COLOR),
    staleTime: 5 * 60 * 1000,
  });
  return data && /^#[0-9a-f]{6}$/i.test(data) ? data : DEFAULT_HIGHLIGHT_COLOR;
}

/**
 * How highlighted text is painted, in the record's text and in Settings' sample alike. The padding
 * sits inside the background, and box-decoration-break repeats it on every line of a wrapped
 * highlight rather than only at its two ends.
 */
export function useHighlightStyle() {
  const theme = useTheme();
  const color = useHighlightColor();
  return {
    backgroundColor: color,
    color: theme.palette.getContrastText(color),
    padding: '0.1em 0.4em',
    borderRadius: '4px',
    boxDecorationBreak: 'clone',
    WebkitBoxDecorationBreak: 'clone',
  } as const;
}
