import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Box, Button, CircularProgress, IconButton, Menu, MenuItem, Stack, Tooltip, Typography } from '@mui/material';
import ChevronLeft from '@mui/icons-material/ChevronLeft';
import ChevronRight from '@mui/icons-material/ChevronRight';
import ChecklistIcon from '@mui/icons-material/Checklist';
import RefreshIcon from '@mui/icons-material/Refresh';
import {
  getChecklistIndex,
  getChecklistWeek,
  setSetting,
  type ChecklistGroup,
  type ChecklistMark,
  type ChecklistWeek,
} from '../api/client';
import { dirOf } from '../util/rtl';

/*
 * The phone's checklists for one week, as a read-only wall dashboard — opened in its own tab,
 * with no navigation or footer around it.
 *
 * Built to be glanced at on a 16:9 screen: the groups flow into as many columns as the width
 * holds, and the whole page is fitted to the window — the font steps down until nothing overflows
 * — so it never scrolls. The data is the week file the phone saves (synced into the notes folder).
 *
 * Hands-off: it watches the files (the backend reports when they last changed; asked every few
 * seconds) and re-reads the week as soon as the phone's edit arrives, and it reloads itself every
 * few minutes — a setting, changed from the header — which also brings in a new deploy and moves
 * the today shading past midnight. `?reload=N` overrides the setting for one screen.
 */

const DAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const MAX_FONT = 30;
const MIN_FONT = 7;
/** How often to ask whether the week files changed — a folder listing, cheap. */
const CHANGE_POLL_MS = 15_000;
const RELOAD_SETTING = 'checklists.dashboard.reloadMinutes';
const RELOAD_CHOICES = [0, 1, 2, 4, 10, 30, 60];

const ACCENT = '#2e7d32';
const ACCENT_SOFT = '#a5d6a7';
const CODE_GREY = '#9e9e9e';
/** Today's column, shaded through every group so the day reads as one band down the page. */
const TODAY_SHADE = '#e8f5e9';
/** The row under the mouse, so the eye can follow it across the day columns. */
const ROW_HOVER = '#fff3c4';
/** The same row where it crosses today's column: still amber, a shade deeper, so both read. */
const ROW_HOVER_TODAY = '#f3e3a0';

function addDays(iso: string, n: number): Date {
  const d = new Date(`${iso}T00:00:00`);
  d.setDate(d.getDate() + n);
  return d;
}

function sameDay(a: Date, b: Date) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

function pct(x: number | null) {
  return x == null ? '–' : `${Math.round(x * 100)}%`;
}

const score = (m: ChecklistMark) => (m === 'DONE' ? 1 : m === 'PARTIAL' ? 0.5 : 0);

function dayScore(week: ChecklistWeek, day: number): number | null {
  const marks = week.groups.filter((g) => !g.weekly).flatMap((g) => g.items.map((i) => i.marks[day]));
  return marks.length ? marks.reduce((s, m) => s + score(m), 0) / marks.length : null;
}

function groupScore(g: ChecklistGroup): number | null {
  const marks = g.items.flatMap((i) => i.marks);
  return marks.length ? marks.reduce((s, m) => s + score(m), 0) / marks.length : null;
}

/** "19–25 Oct 2026", or "28 Sep – 4 Oct 2026" across a month. */
function weekRange(week: ChecklistWeek) {
  const a = addDays(week.monday, 0);
  const b = addDays(week.monday, 6);
  const mon = (d: Date) => d.toLocaleDateString(undefined, { month: 'short' });
  return a.getMonth() === b.getMonth()
    ? `${a.getDate()}–${b.getDate()} ${mon(b)} ${b.getFullYear()}`
    : `${a.getDate()} ${mon(a)} – ${b.getDate()} ${mon(b)} ${b.getFullYear()}`;
}

/** One mark: filled for done, half-shaded for partial, an outline for not done; future days faded. */
function MarkCell({ mark, future, today }: { mark: ChecklistMark; future: boolean; today: boolean }) {
  return (
    <Box
      sx={{
        width: '1.25em',
        height: '1.25em',
        mx: 'auto',
        borderRadius: '0.25em',
        border: '0.1em solid',
        borderColor: mark === 'NONE' ? (today ? ACCENT : '#c8c8c8') : ACCENT,
        bgcolor: mark === 'DONE' ? ACCENT : mark === 'PARTIAL' ? ACCENT_SOFT : 'transparent',
        color: mark === 'DONE' ? '#fff' : '#1b5e20',
        opacity: future ? 0.35 : 1,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: '0.8em',
        fontWeight: 700,
        lineHeight: 1,
      }}
    >
      {mark === 'DONE' ? '✓' : mark === 'PARTIAL' ? '◐' : ''}
    </Box>
  );
}

function GroupBlock({ group, week, today }: { group: ChecklistGroup; week: ChecklistWeek; today: Date }) {
  const days = group.weekly ? [0] : [0, 1, 2, 3, 4, 5, 6];
  const markCols = group.weekly ? '2.4em' : 'repeat(7, 1.6em)';
  const s = groupScore(group);
  return (
    <Box data-group sx={{ breakInside: 'avoid', mb: '0.9em', pr: '0.6em' }}>
      <Box sx={{ display: 'grid', gridTemplateColumns: `minmax(0, 1fr) ${markCols}`, alignItems: 'end', columnGap: '0.15em', mb: '0.2em' }}>
        <Box sx={{ fontWeight: 700, color: ACCENT, fontSize: '1.15em', lineHeight: 1.2, display: 'flex', gap: '0.4em', alignItems: 'baseline' }}>
          <span style={{ color: CODE_GREY, fontWeight: 400, fontSize: '0.7em' }}>{group.code}</span>
          <span dir="auto">{group.title}</span>
          <span style={{ marginLeft: 'auto', fontSize: '0.75em', paddingRight: '0.3em' }}>{pct(s)}</span>
        </Box>
        {days.map((d) => {
          const date = addDays(week.monday, d);
          const isToday = !group.weekly && sameDay(date, today);
          return (
            <Box
              key={d}
              sx={{
                textAlign: 'center',
                fontSize: '0.7em',
                color: isToday ? ACCENT : 'text.secondary',
                fontWeight: isToday ? 700 : 400,
                bgcolor: isToday ? TODAY_SHADE : 'transparent',
                borderRadius: '0.35em 0.35em 0 0',
                pt: '0.15em',
              }}
            >
              {group.weekly ? 'Week' : DAY_NAMES[d].slice(0, 2)}
            </Box>
          );
        })}
      </Box>
      {group.items.map((item) => (
        <Box
          key={item.code}
          sx={{
            display: 'grid',
            gridTemplateColumns: `minmax(0, 1fr) ${markCols}`,
            // Cells stretch to the row's full height and carry the padding themselves, so a
            // shaded day column is continuous from row to row rather than striped.
            alignItems: 'stretch',
            columnGap: '0.15em',
            borderTop: '1px solid #f0f0f0',
            '&:hover': { bgcolor: ROW_HOVER },
            '&:hover [data-today]': { bgcolor: ROW_HOVER_TODAY },
          }}
        >
          {/* Arabic names read right to left from the cell's right edge, code first; the rest left to right. */}
          <Box
            dir={dirOf(item.text)}
            sx={{ minWidth: 0, lineHeight: 1.15, overflowWrap: 'anywhere', py: '0.12em', alignSelf: 'center', textAlign: 'start' }}
          >
            <span dir="ltr" style={{ color: CODE_GREY, fontSize: '0.65em', marginInlineEnd: '0.35em' }}>
              {group.code}.{item.code}
            </span>
            <span>{item.text}</span>
          </Box>
          {days.map((d) => {
            const date = addDays(week.monday, d);
            const isToday = !group.weekly && sameDay(date, today);
            return (
              <Box
                key={d}
                data-today={isToday || undefined}
                sx={{
                  py: '0.12em',
                  display: 'flex',
                  alignItems: 'center',
                  bgcolor: isToday ? TODAY_SHADE : 'transparent',
                }}
              >
                <MarkCell
                  mark={item.marks[d] ?? 'NONE'}
                  future={!group.weekly && date > today && !isToday}
                  today={isToday}
                />
              </Box>
            );
          })}
        </Box>
      ))}
      {group.items.length === 0 && <Box sx={{ color: 'text.disabled', fontSize: '0.8em' }}>—</Box>}
    </Box>
  );
}

/**
 * Fills the window: groups balanced across columns, at the largest font that fits without scrolling.
 *
 * Tries 3 to 6 columns; for each, steps the font down from MAX_FONT until nothing overflows (with a
 * fixed height, overflow in a multi-column box appears as extra columns — scrollWidth — or as
 * height), then keeps whichever column count allowed the biggest font. Fewer, wider columns usually
 * win when the list is short; more columns when it is long. Re-run on new data or a resize.
 */
const COLUMN_CHOICES = [3, 4, 5, 6];

/**
 * How many font-sizes (em) one column needs to stay readable: the seven day boxes (7 × 1.6em), the
 * gaps, and about 12 characters of name. A font larger than the column width allows would squeeze
 * the names into one-word lines spilling over the boxes, so each column count starts below that.
 */
const COLUMN_EMS = 7 * 1.6 + 1.4 + 0.6 + 12;

function startFont(cols: number, width: number) {
  const colWidth = (width - 32) / cols;
  return Math.max(MIN_FONT, Math.min(MAX_FONT, Math.floor((colWidth / COLUMN_EMS) * 2) / 2));
}

function FittedColumns({ week }: { week: ChecklistWeek }) {
  const box = useRef<HTMLDivElement>(null);
  const [size, setSize] = useState({ w: window.innerWidth, h: window.innerHeight });
  const [trial, setTrial] = useState({ i: 0, font: startFont(COLUMN_CHOICES[0], window.innerWidth) });
  const [best, setBest] = useState<{ cols: number; font: number } | null>(null);
  const [found, setFound] = useState<{ cols: number; font: number }[]>([]);
  const today = useMemo(() => new Date(), [week]);

  useEffect(() => {
    const onResize = () => setSize({ w: window.innerWidth, h: window.innerHeight });
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  // New data or a new window size: search again.
  useLayoutEffect(() => {
    setBest(null);
    setFound([]);
    setTrial({ i: 0, font: startFont(COLUMN_CHOICES[0], size.w) });
  }, [week, size]);

  useLayoutEffect(() => {
    if (best) return;
    const el = box.current;
    if (!el) return;
    // Not fitting: content beyond the box, or a group broken across two columns.
    const split = [...el.querySelectorAll('[data-group]')].some((g) => g.getClientRects().length > 1);
    const overflows = el.scrollWidth > el.clientWidth + 1 || el.scrollHeight > el.clientHeight + 1 || split;
    if (overflows && trial.font > MIN_FONT) {
      setTrial((t) => ({ ...t, font: Math.max(MIN_FONT, t.font - 0.5) }));
      return;
    }
    // This column count's best font is found; try the next, or settle on the winner.
    const results = [...found, { cols: COLUMN_CHOICES[trial.i], font: trial.font }];
    if (trial.i + 1 < COLUMN_CHOICES.length) {
      setFound(results);
      setTrial({ i: trial.i + 1, font: startFont(COLUMN_CHOICES[trial.i + 1], size.w) });
    } else {
      // Biggest font wins; on a tie, fewer columns (wider rows, fewer wrapped names).
      setBest(results.reduce((a, b) => (b.font > a.font ? b : a)));
    }
  }, [trial, best, found, size]);

  const cols = best?.cols ?? COLUMN_CHOICES[trial.i];
  const font = best?.font ?? trial.font;

  return (
    <Box
      ref={box}
      sx={{
        flex: 1,
        minHeight: 0,
        overflow: 'hidden',
        columnCount: cols,
        columnGap: '1.4em',
        columnFill: 'balance',
        columnRule: '1px solid #eeeeee',
        fontSize: `${font}px`,
        px: '1em',
        pt: '0.6em',
        // Hidden until the search settles, so the trials never flash on screen.
        visibility: best ? 'visible' : 'hidden',
      }}
    >
      {week.groups.map((g) => (
        <GroupBlock key={g.code} group={g} week={week} today={today} />
      ))}
    </Box>
  );
}

export function ChecklistDashboardPage() {
  const [params, setParams] = useSearchParams();
  const { data: index, isError: indexError } = useQuery({
    queryKey: ['checklist-index'],
    queryFn: getChecklistIndex,
    refetchInterval: CHANGE_POLL_MS,
    refetchIntervalInBackground: true,
  });

  // The weeks to step through: every week with a file, plus this week once an earlier one exists —
  // not started yet, it shows the last week's items with no marks (the backend carries them over).
  const weeks = useMemo(() => {
    if (!index) return [];
    const all = index.weeks;
    return all.includes(index.current) || !all.some((w) => w < index.current) ? all : [...all, index.current].sort();
  }, [index]);

  // The week asked for, else this week, else (no earlier week to carry over) the latest there is.
  const weekId = useMemo(() => {
    const asked = params.get('week');
    if (asked) return asked;
    if (!index) return null;
    return weeks.includes(index.current) ? index.current : weeks.at(-1) ?? null;
  }, [params, index, weeks]);

  const { data: week, isLoading, isError } = useQuery({
    queryKey: ['checklist-week', weekId],
    queryFn: () => getChecklistWeek(weekId!),
    enabled: !!weekId,
  });

  // A file changed: re-read the week in place (the old one stays up while the new one loads).
  const queryClient = useQueryClient();
  const changed = index?.changed;
  const seenChange = useRef<number | undefined>(undefined);
  useEffect(() => {
    if (changed === undefined) return;
    if (seenChange.current !== undefined && changed !== seenChange.current) {
      queryClient.invalidateQueries({ queryKey: ['checklist-week'] });
    }
    seenChange.current = changed;
  }, [changed, queryClient]);

  const urlReload = params.get('reload');
  const reloadMinutes = urlReload !== null && !Number.isNaN(Number(urlReload))
    ? Math.max(0, Number(urlReload))
    : index?.reloadMinutes;
  useFullReload(reloadMinutes);

  useEffect(() => {
    document.title = week ? `Checklists · Week ${week.week}` : 'Checklists';
  }, [week]);

  const at = weekId ? weeks.indexOf(weekId) : -1;
  const go = (id: string | undefined) => id && setParams({ week: id });

  // A failed refetch keeps what is on the wall; only a page with nothing to show says so.
  if ((indexError && !index) || (weekId && isError && !week)) {
    return <Message text={weekId ? `No checklist file for ${weekId}.` : 'Could not read the checklists.'} />;
  }
  if (index && !weekId) {
    return <Message text={`No checklists yet — the phone writes them to ${index.dir}.`} />;
  }
  if (isLoading || !week) {
    return (
      <Box sx={{ height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <CircularProgress />
      </Box>
    );
  }

  const today = new Date();
  return (
    <Box sx={{ height: '100vh', width: '100vw', overflow: 'hidden', display: 'flex', flexDirection: 'column', bgcolor: '#fff' }}>
      {/* Header: the week, its navigation, and each day's completion. */}
      <Stack direction="row" alignItems="center" spacing={1.5} sx={{ px: 2, py: 0.75, borderBottom: '1px solid #e0e0e0', flexShrink: 0 }}>
        <ChecklistIcon sx={{ color: ACCENT }} />
        <Typography sx={{ fontWeight: 700, fontSize: 20, whiteSpace: 'nowrap' }}>Week {week.week}</Typography>
        <Typography sx={{ color: 'text.secondary', fontSize: 14, whiteSpace: 'nowrap' }}>{weekRange(week)}</Typography>
        {week.carriedFrom && (
          <Typography sx={{ color: 'text.disabled', fontSize: 12, whiteSpace: 'nowrap' }}>
            not started · items from {week.carriedFrom}
          </Typography>
        )}
        <Tooltip title="Previous week">
          <span>
            <IconButton size="small" disabled={at <= 0} onClick={() => go(weeks[at - 1])}>
              <ChevronLeft />
            </IconButton>
          </span>
        </Tooltip>
        <Tooltip title="Next week">
          <span>
            <IconButton size="small" disabled={at < 0 || at >= weeks.length - 1} onClick={() => go(weeks[at + 1])}>
              <ChevronRight />
            </IconButton>
          </span>
        </Tooltip>
        <Box sx={{ flex: 1 }} />
        <ReloadControl minutes={reloadMinutes} fromUrl={urlReload !== null} />
        <Stack direction="row" spacing={0.75}>
          {DAY_NAMES.map((name, d) => {
            const date = addDays(week.monday, d);
            const isToday = sameDay(date, today);
            const s = dayScore(week, d);
            return (
              <Box
                key={name}
                sx={{
                  minWidth: 54,
                  textAlign: 'center',
                  px: 0.75,
                  py: 0.25,
                  borderRadius: 1,
                  border: '1px solid',
                  borderColor: isToday ? ACCENT : '#e0e0e0',
                  bgcolor: isToday ? '#e8f5e9' : 'transparent',
                  opacity: date > today && !isToday ? 0.5 : 1,
                }}
              >
                <Box sx={{ fontSize: 11, fontWeight: isToday ? 700 : 400, lineHeight: 1.2 }}>
                  {name} {date.getDate()}
                </Box>
                <Box sx={{ fontSize: 14, fontWeight: 700, color: ACCENT, lineHeight: 1.2 }}>{pct(s)}</Box>
              </Box>
            );
          })}
        </Stack>
      </Stack>
      <FittedColumns week={week} />
    </Box>
  );
}

/**
 * Reload the whole page every [minutes] (0 or unknown: never). Only when the server answers — a
 * reload while it is down would leave the browser's error page up for good, and nothing on it
 * would ever try again; skipped, the next round tries.
 */
function useFullReload(minutes: number | undefined) {
  useEffect(() => {
    if (!minutes) return;
    const timer = window.setInterval(() => {
      getChecklistIndex().then(() => window.location.reload(), () => {});
    }, minutes * 60_000);
    return () => window.clearInterval(timer);
  }, [minutes]);
}

/** The reload interval, shown small in the header; a click picks another (saved as a setting). */
function ReloadControl({ minutes, fromUrl }: { minutes: number | undefined; fromUrl: boolean }) {
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const queryClient = useQueryClient();
  if (minutes === undefined) return null;
  const pick = async (m: number) => {
    setAnchor(null);
    await setSetting(RELOAD_SETTING, String(m));
    queryClient.invalidateQueries({ queryKey: ['checklist-index'] });
  };
  return (
    <>
      <Tooltip title={fromUrl ? 'Reload interval set by ?reload= in the address' : 'Reloads itself — click to change'}>
        <Button
          size="small"
          startIcon={<RefreshIcon sx={{ fontSize: 16 }} />}
          onClick={(e) => setAnchor(e.currentTarget)}
          sx={{ color: 'text.secondary', fontSize: 12, minWidth: 0, whiteSpace: 'nowrap', textTransform: 'none' }}
        >
          {minutes ? `${minutes} min` : 'off'}
        </Button>
      </Tooltip>
      <Menu anchorEl={anchor} open={!!anchor} onClose={() => setAnchor(null)}>
        {RELOAD_CHOICES.map((m) => (
          <MenuItem key={m} selected={m === minutes} onClick={() => pick(m)} dense>
            {m ? `Reload every ${m} min` : 'Never reload'}
          </MenuItem>
        ))}
      </Menu>
    </>
  );
}

function Message({ text }: { text: string }) {
  return (
    <Box sx={{ height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <Stack spacing={1.5} alignItems="center">
        <ChecklistIcon sx={{ fontSize: 48, color: 'text.disabled' }} />
        <Typography color="text.secondary">{text}</Typography>
      </Stack>
    </Box>
  );
}
