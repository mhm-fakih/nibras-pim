import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Box, Tooltip, Typography } from '@mui/material';
import ArrowUpward from '@mui/icons-material/ArrowUpward';
import ArrowDownward from '@mui/icons-material/ArrowDownward';
import KeyboardDoubleArrowUp from '@mui/icons-material/KeyboardDoubleArrowUp';
import KeyboardDoubleArrowDown from '@mui/icons-material/KeyboardDoubleArrowDown';
import DragHandle from '@mui/icons-material/DragHandle';
import { getIndicatorTrends, IndicatorTrend } from '../api/client';
import { dirOf } from '../util/rtl';
import { DEFAULT_TICKER_THRESHOLD, Move, TickerConfig, moveOf } from '../util/trend';

export function tickerConfig(config?: string): TickerConfig {
  try {
    return config ? (JSON.parse(config) as TickerConfig) : {};
  } catch {
    return {};
  }
}

const fmt = (n: number) => (Number.isInteger(n) ? String(n) : n.toFixed(2));

function MoveIcon({ move }: { move: Move }) {
  const sx = { fontSize: '1.25rem' };
  switch (move) {
    case 'up2':
      return <KeyboardDoubleArrowUp sx={{ ...sx, color: 'success.main' }} />;
    case 'up':
      return <ArrowUpward sx={{ ...sx, color: 'success.main' }} />;
    case 'down':
      return <ArrowDownward sx={{ ...sx, color: 'error.main' }} />;
    case 'down2':
      return <KeyboardDoubleArrowDown sx={{ ...sx, color: 'error.main' }} />;
    case 'same':
      return <DragHandle sx={{ ...sx, color: 'text.secondary' }} />;
    default:
      return <Box component="span" sx={{ width: '1.25rem', textAlign: 'center', color: 'text.disabled' }}>·</Box>;
  }
}

const MOVE_WORD: Record<Move, string> = {
  up2: 'Up sharply',
  up: 'Up',
  same: 'Same level',
  down: 'Down',
  down2: 'Down sharply',
  none: 'Not enough values yet',
};

/** The bookmarked (monitored) indicators as a ticker: latest value, arrow, change since the one before. */
export function IndicatorTickerComponent({ config }: { config?: string }) {
  const { data, isLoading, error } = useQuery({
    queryKey: ['indicator-trends'],
    queryFn: getIndicatorTrends,
    staleTime: 0,
    refetchInterval: 5 * 60 * 1000,
  });
  if (isLoading) return <Typography color="text.secondary">Loading…</Typography>;
  if (error) return <Typography color="error">Couldn't load the indicators.</Typography>;
  return <IndicatorTickerView trends={data ?? []} config={config} />;
}

export function IndicatorTickerView({ trends, config }: { trends: IndicatorTrend[]; config?: string }) {
  const navigate = useNavigate();
  const cfg = tickerConfig(config);
  const threshold = cfg.threshold && cfg.threshold > 0 ? cfg.threshold : DEFAULT_TICKER_THRESHOLD;
  const wanted = (cfg.categories ?? '')
    .split(',')
    .map((c: string) => c.trim().toLowerCase())
    .filter(Boolean);
  const rows = trends.filter((t) => wanted.length === 0 || wanted.includes((t.category ?? '').toLowerCase()));
  if (rows.length === 0) {
    return <Typography color="text.secondary">No monitored (bookmarked) indicators.</Typography>;
  }
  return (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
        columnGap: 2,
        rowGap: 0.25,
      }}
    >
      {rows.map((t) => {
        const move = moveOf(t.value, t.previous, threshold);
        const delta = t.value != null && t.previous != null ? t.value - t.previous : null;
        const pct = delta != null && t.previous ? (delta / Math.abs(t.previous)) * 100 : null;
        const when = (d?: string | null) => (d ? new Date(d).toLocaleString() : '—');
        return (
          <Tooltip
            key={t.id}
            title={`${MOVE_WORD[move]} · latest ${t.value == null ? '—' : fmt(t.value)} (${when(t.date)}) · before ${
              t.previous == null ? '—' : fmt(t.previous)
            } (${when(t.previousDate)})`}
          >
            <Box
              onClick={() => navigate('/indicators')}
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 0.75,
                py: 0.25,
                px: 0.5,
                borderRadius: 1,
                cursor: 'pointer',
                minWidth: 0,
                '&:hover': { bgcolor: 'action.hover' },
              }}
            >
              <MoveIcon move={move} />
              <Typography
                variant="body2"
                dir={dirOf(t.name)}
                sx={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
              >
                {t.name}
              </Typography>
              <Typography variant="body2" sx={{ fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>
                {t.value == null ? '—' : fmt(t.value)}
              </Typography>
              <Typography
                variant="caption"
                sx={{
                  minWidth: '3.5em',
                  textAlign: 'end',
                  fontVariantNumeric: 'tabular-nums',
                  color: delta == null || delta === 0 ? 'text.secondary' : delta > 0 ? 'success.main' : 'error.main',
                }}
              >
                {delta == null
                  ? ''
                  : `${delta > 0 ? '+' : ''}${fmt(delta)}${pct != null ? ` (${pct > 0 ? '+' : ''}${Math.round(pct)}%)` : ''}`}
              </Typography>
            </Box>
          </Tooltip>
        );
      })}
    </Box>
  );
}
