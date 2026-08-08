import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Box,
  Button,
  Card,
  CardContent,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import ShowChart from '@mui/icons-material/ShowChart';
import {
  EntityRecord,
  SeriesPoint,
  createEntity,
  getIndicatorSeries,
  listEntities,
  saveIndicatorData,
} from '../api/client';

const DAYS_SHOWN = 40;
const PER_ROW = 10;
const ROWS = Math.ceil(DAYS_SHOWN / PER_ROW);
const CELL = 16;
const GAP = 3;

/** Red (no data) / orange (1-2) / light green (3-4) / dark green (5+) — a fixed legend, not tied
 * to the indicator's own colour, so intensity reads the same across every habit at a glance. */
const NO_DATA_COLOR = '#e53935';
const LOW_COLOR = '#fb8c00';
const MID_COLOR = '#66bb6a';
const HIGH_COLOR = '#2e7d32';
/** A day after today — hasn't happened yet, so it's neither "no data" nor coloured by a count. */
const FUTURE_COLOR = '#e0e0e0';

function cellColor(hasEntry: boolean, total: number): string {
  if (!hasEntry) return NO_DATA_COLOR;
  if (total <= 2) return LOW_COLOR;
  if (total <= 4) return MID_COLOR;
  return HIGH_COLOR;
}

function ymd(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
    d.getDate(),
  ).padStart(2, '0')}`;
}

/**
 * A GitHub-style day heatmap + day-by-day log for one habit indicator, plus a quick "log today"
 * entry. Meant to be embedded in a Goal's detail view when a daily-frequency indicator is linked
 * to it (Indicator.goalId) — see DetailPane.tsx. `readOnly` drops the entry form and the
 * day-by-day list, leaving just the heatmap grid — for glance-only display (the Kanban TV panel).
 * `preloadedSeries`, when given, skips this component's own query entirely — used by the public
 * (unauthenticated) dashboard view, which fetches everything in one server-resolved snapshot call
 * rather than each heatmap making its own authenticated request.
 */
export function HabitHeatmap({
  indicator,
  readOnly,
  preloadedSeries,
}: {
  indicator: EntityRecord;
  readOnly?: boolean;
  preloadedSeries?: SeriesPoint[];
}) {
  const qc = useQueryClient();

  const { data: fetchedSeries } = useQuery({
    queryKey: ['indicator-series', indicator.id],
    queryFn: () => getIndicatorSeries(indicator.id),
    enabled: preloadedSeries === undefined,
  });
  const series = preloadedSeries ?? fetchedSeries;

  // Multiple progress reports can land on the same day — keep all of them (not just the last one
  // written) so the tooltip and the day-by-day list below both show the full picture.
  const byDay = useMemo(() => {
    const m = new Map<string, { value: number; notes?: string }[]>();
    (series ?? []).forEach((p) => {
      const key = p.date.slice(0, 10);
      const arr = m.get(key);
      if (arr) arr.push({ value: p.value, notes: p.notes });
      else m.set(key, [{ value: p.value, notes: p.notes }]);
    });
    return m;
  }, [series]);

  const today = new Date();
  today.setHours(0, 0, 0, 0);

  // DAYS_SHOWN days starting from whichever is later: the indicator's creation day, or
  // DAYS_SHOWN-1 days ago. A brand-new indicator's grid genuinely starts on the day it was
  // created (today falls near the top-left, everything after it still to come); an older one
  // falls back to the usual trailing window ending today. Either way today is always inside the
  // window, so days after it (only possible in the fresh-indicator case) are real future days.
  const days = useMemo(() => {
    const created = new Date(indicator.dateCreated);
    created.setHours(0, 0, 0, 0);
    const trailingStart = new Date(today);
    trailingStart.setDate(trailingStart.getDate() - (DAYS_SHOWN - 1));
    const start = created > trailingStart ? created : trailingStart;
    return Array.from({ length: DAYS_SHOWN }, (_, i) => {
      const d = new Date(start);
      d.setDate(start.getDate() + i);
      return d;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [indicator.dateCreated]);

  const todayYmd = ymd(today);
  const [value, setValue] = useState('1');
  const [notes, setNotes] = useState('');
  // Backfilling matters: a habit is often logged the morning after. Defaults to today, and resets
  // back to today after each save so the common case stays one click.
  const [logDate, setLogDate] = useState(todayYmd);
  // saveData() always inserts a new row (never updates in place), so "today" can genuinely hold
  // several reports — surface all of them, not just whichever loaded last.
  const todayEntries = byDay.get(todayYmd) ?? [];

  const log = useMutation({
    mutationFn: () =>
      saveIndicatorData(indicator.id, Number(value) || 0, logDate || undefined, notes || undefined),
    onSuccess: () => {
      setNotes('');
      setLogDate(todayYmd);
      qc.invalidateQueries({ queryKey: ['indicator-series', indicator.id] });
      qc.invalidateQueries({ queryKey: ['indicators-due'] });
    },
  });

  const logEntries = (series ?? []).slice().reverse(); // newest first, for the day-by-day list

  return (
    <Card variant="outlined">
      <CardContent sx={readOnly ? { p: 1, '&:last-child': { pb: 1 } } : undefined}>
        {/* readOnly (dashboard/glance contexts) drops this — the indicator's own name just
            repeats whatever already labels it one level up (e.g. the goal's code), and every bit
            of height matters when several of these are stacked in one column. */}
        {!readOnly && (
          <Stack direction="row" alignItems="baseline" spacing={1} sx={{ mb: 1.5 }}>
            <Typography sx={{ fontWeight: 600, flex: 1 }} dir="auto">
              {indicator.displayTitle || (indicator.name as string) || 'Habit'}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {(series ?? []).length} day{(series ?? []).length === 1 ? '' : 's'} logged
            </Typography>
          </Stack>
        )}

        {/* Heatmap grid: last DAYS_SHOWN days, PER_ROW per row, oldest first. */}
        <Box sx={{ overflowX: 'auto', pb: 0.5 }}>
          <svg
            width={PER_ROW * (CELL + GAP)}
            height={ROWS * (CELL + GAP)}
            role="img"
            aria-label={`${indicator.name ?? 'Habit'} day heatmap`}
          >
            {days.map((day, i) => {
              const key = ymd(day);
              const isFuture = key > todayYmd;
              const entries = isFuture ? undefined : byDay.get(key);
              const total = entries?.reduce((s, e) => s + e.value, 0) ?? 0;
              const fill = isFuture ? FUTURE_COLOR : cellColor(Boolean(entries), total);
              const col = i % PER_ROW;
              const row = Math.floor(i / PER_ROW);
              const x = col * (CELL + GAP);
              const y = row * (CELL + GAP);
              if (isFuture) {
                return (
                  <rect
                    key={key}
                    x={x}
                    y={y}
                    width={CELL}
                    height={CELL}
                    rx={3}
                    fill={fill}
                    stroke="rgba(0,0,0,0.1)"
                  />
                );
              }
              const label = (
                <Box component="span" sx={{ display: 'block' }}>
                  <Box component="span" sx={{ display: 'block', fontWeight: 600 }}>
                    {day.toLocaleDateString()}
                  </Box>
                  {entries
                    ? entries.map((e, ei) => (
                        <Box component="span" key={ei} sx={{ display: 'block' }}>
                          {e.value}
                          {e.notes ? `: ${e.notes}` : ''}
                        </Box>
                      ))
                    : <Box component="span" sx={{ display: 'block' }}>no entry</Box>}
                </Box>
              );
              return (
                <Tooltip key={key} title={label}>
                  <g>
                    <rect
                      x={x}
                      y={y}
                      width={CELL}
                      height={CELL}
                      rx={3}
                      fill={fill}
                      stroke="rgba(0,0,0,0.15)"
                    />
                    {/* More than one report that day — show the count, not just the colour. */}
                    {entries && entries.length > 1 && (
                      <text
                        x={x + CELL / 2}
                        y={y + CELL / 2}
                        textAnchor="middle"
                        dominantBaseline="central"
                        fontSize={CELL * 0.5}
                        fontWeight={700}
                        fill="#fff"
                        style={{ pointerEvents: 'none', userSelect: 'none' }}
                      >
                        {entries.length}
                      </text>
                    )}
                  </g>
                </Tooltip>
              );
            })}
          </svg>
        </Box>

        {!readOnly && (
          <>
            {/* Quick "log today" entry */}
            <Stack
              component="form"
              onSubmit={(e) => {
                e.preventDefault();
                log.mutate();
              }}
              direction="row"
              spacing={1}
              alignItems="center"
              sx={{ mt: 1.5, mb: 1.5 }}
            >
              <TextField
                size="small"
                type="number"
                label="Value"
                value={value}
                onChange={(e) => setValue(e.target.value)}
                sx={{ width: 90 }}
              />
              <TextField
                size="small"
                type="date"
                label="Date"
                value={logDate}
                onChange={(e) => setLogDate(e.target.value)}
                InputLabelProps={{ shrink: true }}
                sx={{ width: 160 }}
              />
              <TextField
                size="small"
                label="Note"
                placeholder="What did you do today?"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                fullWidth
              />
              <Button type="submit" variant="contained" size="small" disabled={log.isPending}>
                {todayEntries.length > 0 ? 'Log another' : 'Log'}
              </Button>
            </Stack>
            {todayEntries.length > 0 && (
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
                Already logged today ({todayEntries.length}):{' '}
                {todayEntries
                  .map((e) => `${e.value}${e.notes ? `: ${e.notes}` : ''}`)
                  .join('; ')}
              </Typography>
            )}

            {/* Day-by-day log, newest first */}
            {logEntries.length > 0 && (
              <Box sx={{ maxHeight: 220, overflowY: 'auto' }}>
                <Stack spacing={0.5}>
                  {logEntries.map((p, i) => (
                    <Stack
                      key={`${p.date}-${i}`}
                      direction="row"
                      spacing={1}
                      sx={{ py: 0.5, borderBottom: 1, borderColor: 'divider' }}
                    >
                      <Typography variant="caption" color="text.secondary" sx={{ width: 90, flexShrink: 0 }}>
                        {new Date(p.date).toLocaleDateString()}
                      </Typography>
                      <Typography variant="caption" sx={{ fontWeight: 600, width: 32, flexShrink: 0 }}>
                        {p.value}
                      </Typography>
                      <Typography variant="caption" color="text.secondary" dir="auto" noWrap>
                        {p.notes}
                      </Typography>
                    </Stack>
                  ))}
                </Stack>
              </Box>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}

/** Every indicator linked to a goal (Indicator.goalId), each shown as its own habit heatmap. When
 *  none is linked yet, offers a one-click way to create one — the daily/manual/bookmarked defaults
 *  that make an indicator work as a habit tracker without a trip through the Indicators form. */
export function GoalHabits({ goalId, goalTitle }: { goalId: number; goalTitle?: string }) {
  const qc = useQueryClient();
  const { data } = useQuery({
    queryKey: ['indicators', 'goal', goalId],
    queryFn: () => listEntities('indicators', { goalId, size: 50 }),
  });
  const indicators = data?.content ?? [];

  const track = useMutation({
    mutationFn: () =>
      createEntity('indicators', {
        name: goalTitle ? `${goalTitle} (habit)` : 'Habit',
        frequency: 'daily',
        bookmarked: true,
        goalId,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['indicators', 'goal', goalId] });
      qc.invalidateQueries({ queryKey: ['list', 'indicators'] });
    },
  });

  if (indicators.length === 0) {
    return (
      <Box sx={{ mt: 2 }}>
        <Button
          size="small"
          variant="outlined"
          startIcon={<ShowChart fontSize="small" />}
          disabled={track.isPending}
          onClick={() => track.mutate()}
        >
          Track this goal
        </Button>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
          Adds a daily habit indicator linked to this goal, with a day-by-day log below.
        </Typography>
      </Box>
    );
  }
  return (
    <Stack spacing={2} sx={{ mt: 2 }}>
      {/* One indicator per goal: the "add another" button is deliberately gone — a goal tracks a
          single habit, and extra indicators only fragmented its history across heatmaps. */}
      {indicators.map((i) => (
        <HabitHeatmap key={i.id} indicator={i} />
      ))}
    </Stack>
  );
}
