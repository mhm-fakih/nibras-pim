import { useMemo, useState, type ReactNode } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Alert, Box, Button, Chip, CircularProgress, Link, Stack, Tooltip, Typography } from '@mui/material';
import ChevronLeft from '@mui/icons-material/ChevronLeft';
import ChevronRight from '@mui/icons-material/ChevronRight';
import OpenInNew from '@mui/icons-material/OpenInNew';
import CheckCircleOutline from '@mui/icons-material/CheckCircleOutline';
import MenuBook from '@mui/icons-material/MenuBook';
import EventNote from '@mui/icons-material/EventNote';
import LocalLibrary from '@mui/icons-material/LocalLibrary';
import StickyNote2 from '@mui/icons-material/StickyNote2';
import Flag from '@mui/icons-material/Flag';
import Create from '@mui/icons-material/Create';
import AccountTree from '@mui/icons-material/AccountTree';
import School from '@mui/icons-material/School';
import ShowChart from '@mui/icons-material/ShowChart';
import Groups from '@mui/icons-material/Groups';
import FormatQuote from '@mui/icons-material/FormatQuote';
import { format, parseISO } from 'date-fns';
import { BASE, getDayFeed, type FeedItem } from '../api/client';
import { ENTITY_BY_KEY } from '../config/entities';
import { useHour12 } from '../hooks/useHour12';
import { fmtHour, fmtTime } from '../util/clock';

const ICONS: Record<string, typeof Flag> = {
  CheckCircleOutline, MenuBook, EventNote, LocalLibrary, StickyNote2, Flag, Create,
  AccountTree, School, ShowChart, Groups, FormatQuote,
};
const MODULE_ICON: Record<string, string> = { indicators: 'ShowChart', contacts: 'Groups', excerpts: 'FormatQuote' };
const MODULE_LABEL: Record<string, string> = { contacts: 'Contacts', indicators: 'Indicators', excerpts: 'Excerpts' };

export function moduleLabel(m: string): string {
  return ENTITY_BY_KEY[m]?.label ?? MODULE_LABEL[m] ?? m.charAt(0).toUpperCase() + m.slice(1);
}

function moduleIcon(m: string) {
  return ICONS[MODULE_ICON[m] ?? ENTITY_BY_KEY[m]?.icon ?? ''] ?? Flag;
}

/** A run of records of one module added one after the other within the same hour. */
interface Run {
  module: string;
  items: FeedItem[];
}

/** Runs longer than this show their first few and a "show all" button (a bulk import, say). */
const RUN_FOLD = 6;

function ymd(d: Date): string {
  return format(d, 'yyyy-MM-dd');
}

/**
 * The Calendar's Feed view: a day's records in the order they were added, by hour — "at 8:00,
 * three resources, then a note" — across every module. The arrows skip to the nearest day before
 * or after that has anything, so empty stretches don't have to be stepped through.
 */
export function DayFeed({
  day,
  onDay,
  hidden,
  onHidden,
  enabledModules,
  dates,
}: {
  day: Date;
  onDay: (d: Date) => void;
  hidden: string[];
  onHidden: (h: string[]) => void;
  /** Modules switched on; records of others are left out. */
  enabledModules: Set<string> | null;
  /** The day's Shamsi/Hijri line and occasions, when the Calendar shows them. */
  dates?: ReactNode;
}) {
  const hour12 = useHour12();
  const { data, isLoading, error } = useQuery({
    queryKey: ['calendar-feed', ymd(day)],
    queryFn: () => getDayFeed(ymd(day)),
  });

  const all = useMemo(
    () => (data?.items ?? []).filter((it) => !enabledModules || enabledModules.has(it.module) || !ENTITY_BY_KEY[it.module]),
    [data, enabledModules],
  );
  const counts = useMemo(() => {
    const c = new Map<string, number>();
    all.forEach((it) => c.set(it.module, (c.get(it.module) ?? 0) + 1));
    return [...c.entries()].sort((a, b) => b[1] - a[1]);
  }, [all]);
  const shown = useMemo(() => all.filter((it) => !hidden.includes(it.module)), [all, hidden]);

  // Hour → runs of one module.
  const hours = useMemo(() => {
    const out: { hour: number; runs: Run[] }[] = [];
    for (const it of shown) {
      const h = parseISO(it.created).getHours();
      let g = out[out.length - 1];
      if (!g || g.hour !== h) {
        g = { hour: h, runs: [] };
        out.push(g);
      }
      const last = g.runs[g.runs.length - 1];
      if (last && last.module === it.module) last.items.push(it);
      else g.runs.push({ module: it.module, items: [it] });
    }
    return out;
  }, [shown]);

  const toggle = (m: string) => onHidden(hidden.includes(m) ? hidden.filter((x) => x !== m) : [...hidden, m]);

  return (
    <Box sx={{ maxWidth: 860, mx: 'auto', px: { xs: 1.5, md: 3 }, py: 2 }}>
      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }} useFlexGap flexWrap="wrap">
        <Tooltip title={data?.previous ? `Previous day with records: ${format(parseISO(data.previous), 'EEE d MMM yyyy')}` : 'Nothing earlier'}>
          <span>
            <Button size="small" startIcon={<ChevronLeft />} disabled={!data?.previous}
              onClick={() => data?.previous && onDay(parseISO(data.previous))}>
              {data?.previous ? format(parseISO(data.previous), 'd MMM yyyy') : 'Earlier'}
            </Button>
          </span>
        </Tooltip>
        <Box sx={{ flex: 1, textAlign: 'center', minWidth: 200 }}>
          <Typography variant="h6" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
            {format(day, 'EEEE d MMMM yyyy')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {isLoading ? '…' : `${shown.length} record${shown.length === 1 ? '' : 's'} added`}
            {shown.length !== all.length ? ` (${all.length - shown.length} hidden)` : ''}
          </Typography>
          {dates}
        </Box>
        <Tooltip title={data?.next ? `Next day with records: ${format(parseISO(data.next), 'EEE d MMM yyyy')}` : 'Nothing later'}>
          <span>
            <Button size="small" endIcon={<ChevronRight />} disabled={!data?.next}
              onClick={() => data?.next && onDay(parseISO(data.next))}>
              {data?.next ? format(parseISO(data.next), 'd MMM yyyy') : 'Later'}
            </Button>
          </span>
        </Tooltip>
      </Stack>

      {counts.length > 0 && (
        <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" sx={{ mb: 2, justifyContent: 'center' }}>
          {counts.map(([m, n]) => {
            const Icon = moduleIcon(m);
            const off = hidden.includes(m);
            return (
              <Chip key={m} size="small" icon={<Icon />} label={`${moduleLabel(m)} ${n}`}
                variant={off ? 'outlined' : 'filled'} color={off ? 'default' : 'primary'}
                onClick={() => toggle(m)} sx={{ opacity: off ? 0.6 : 1 }} />
            );
          })}
        </Stack>
      )}

      {error && <Alert severity="error">Could not load this day: {(error as Error).message}</Alert>}
      {isLoading && <Box sx={{ textAlign: 'center', py: 6 }}><CircularProgress size={28} /></Box>}
      {data?.truncated && (
        <Alert severity="info" sx={{ mb: 2 }}>A module added more than 400 records this day; only its first 400 are listed.</Alert>
      )}
      {!isLoading && !error && shown.length === 0 && (
        <Typography color="text.secondary" sx={{ textAlign: 'center', py: 6 }}>
          Nothing was added on this day.
        </Typography>
      )}

      {hours.map((g) => (
        <Box key={g.hour} sx={{ display: 'flex', gap: 2, mb: 1.5 }}>
          <Box sx={{ width: 56, flexShrink: 0, textAlign: 'right', pt: 0.25 }}>
            <Typography variant="subtitle2" color="text.secondary" sx={{ fontWeight: 700 }}>
              {fmtHour(g.hour, hour12)}
            </Typography>
          </Box>
          <Box sx={{ flex: 1, minWidth: 0, borderLeft: 2, borderColor: 'divider', pl: 2 }}>
            {g.runs.map((run, i) => (
              <RunBlock key={`${run.module}-${run.items[0].id}-${i}`} run={run} hour12={hour12} />
            ))}
          </Box>
        </Box>
      ))}
    </Box>
  );
}

function RunBlock({ run, hour12 }: { run: Run; hour12: boolean }) {
  const [open, setOpen] = useState(false);
  const Icon = moduleIcon(run.module);
  const many = run.items.length > RUN_FOLD && !open;
  const items = many ? run.items.slice(0, RUN_FOLD) : run.items;
  return (
    <Box sx={{ mb: 1.25 }}>
      {run.items.length > 1 && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.25 }}>
          <Icon sx={{ fontSize: 14 }} /> {run.items.length} {moduleLabel(run.module).toLowerCase()}
        </Typography>
      )}
      {items.map((it) => (
        <FeedRow key={`${it.module}-${it.id}`} it={it} hour12={hour12} showIcon={run.items.length === 1} />
      ))}
      {many && (
        <Button size="small" onClick={() => setOpen(true)}>Show all {run.items.length}</Button>
      )}
    </Box>
  );
}

function FeedRow({ it, hour12, showIcon }: { it: FeedItem; hour12: boolean; showIcon: boolean }) {
  const Icon = moduleIcon(it.module);
  const created = parseISO(it.created);
  const edited = it.updated ? parseISO(it.updated) : null;
  const wasEdited = edited && edited.getTime() - created.getTime() > 60_000;
  return (
    <Box sx={{ display: 'flex', gap: 1, alignItems: 'flex-start', py: 0.5, '&:hover .feed-open': { opacity: 1 } }}>
      <Typography variant="caption" color="text.secondary" sx={{ width: hour12 ? 62 : 40, flexShrink: 0, pt: '3px', whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }}>
        {fmtTime(created, hour12)}
      </Typography>
      <Tooltip title={moduleLabel(it.module)}>
        <Icon sx={{ fontSize: 18, mt: '2px', color: showIcon ? 'primary.main' : 'text.disabled', flexShrink: 0 }} />
      </Tooltip>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Stack direction="row" spacing={0.75} alignItems="center" sx={{ minWidth: 0 }}>
          <Link component={RouterLink} to={`/${it.module}?select=${it.id}`} underline="hover" color="text.primary"
            dir="auto" sx={{ fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', minWidth: 0 }}>
            {it.title?.trim() || `${moduleLabel(it.module)} #${it.id}`}
          </Link>
          {it.typeCode && <Chip size="small" label={it.typeCode} sx={{ height: 18, fontSize: 11 }} />}
          {wasEdited && (
            <Tooltip title={`Last edited ${format(edited!, 'd MMM yyyy HH:mm')}`}>
              <Typography variant="caption" color="text.disabled">edited</Typography>
            </Tooltip>
          )}
          <Tooltip title="Open to read, in a new tab">
            <Link className="feed-open" href={`${BASE}read/${it.module}/${it.id}`} target="_blank" rel="noopener"
              sx={{ opacity: 0, display: 'inline-flex', color: 'text.secondary' }}>
              <OpenInNew sx={{ fontSize: 15 }} />
            </Link>
          </Tooltip>
        </Stack>
        {it.snippet && (
          <Typography variant="body2" color="text.secondary" dir="auto"
            sx={{ overflow: 'hidden', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical' }}>
            {it.snippet}
          </Typography>
        )}
      </Box>
    </Box>
  );
}
