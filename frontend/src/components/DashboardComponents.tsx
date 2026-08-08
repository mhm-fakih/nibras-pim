import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Box, Chip, Stack, Tooltip, Typography } from '@mui/material';
import {
  CalendarEvent,
  DashboardFileContent,
  EntityRecord,
  KanbanData,
  PublicDashboardRegion,
  PublicGoalHeatmap,
  SeriesPoint,
  getCalendarEvents,
  getDashboardFileContent,
  getKanban,
  getLookups,
  listEntities,
} from '../api/client';
import { HabitHeatmap } from './HabitHeatmap';
import { RecordCard } from './RecordCard';
import { dirOf } from '../util/rtl';
import { lookupStyle } from './statusStyle';

/** Every component type the dashboard builder supports, and the label shown when picking one. */
export const DASHBOARD_COMPONENT_TYPES: { type: string; label: string }[] = [
  { type: 'quote', label: 'Quote of the day' },
  { type: 'calendar', label: 'Calendar (filtered by type)' },
  { type: 'goalsHeatmap', label: 'Goals habit heatmap' },
  { type: 'kanban', label: 'Kanban board' },
  { type: 'file', label: 'File content' },
];

function safeParse<T>(json: string | undefined, fallback: T): T {
  if (!json) return fallback;
  try {
    return { ...fallback, ...JSON.parse(json) } as T;
  } catch {
    return fallback;
  }
}

function ymd(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Renders whichever component a region is configured for. Unknown/misconfigured types show a
 * plain message instead of breaking the rest of the dashboard. */
export function DashboardComponent({ componentType, config }: { componentType: string; config?: string }) {
  switch (componentType) {
    case 'quote':
      return <QuoteComponent config={config} />;
    case 'calendar':
      return <CalendarComponent config={config} />;
    case 'goalsHeatmap':
      return <GoalsHeatmapComponent />;
    case 'kanban':
      return <KanbanComponent />;
    case 'file':
      return <FileComponent config={config} />;
    default:
      return <Typography color="text.secondary">Unknown component type "{componentType}".</Typography>;
  }
}

// ---- Quote of the day: last bookmarked note of a given type ------------------------

interface QuoteConfig {
  typeCode?: string;
}

function QuoteComponent({ config }: { config?: string }) {
  const { typeCode } = safeParse<QuoteConfig>(config, {});
  const { data: lookups } = useQuery({ queryKey: ['lookups', 'notes'], queryFn: () => getLookups('notes') });
  const typeId = lookups?.types?.find((t) => t.code === typeCode)?.id;
  const { data } = useQuery({
    queryKey: ['dashboard-quote', typeId],
    // "Last bookmarked" has no real timestamp of its own (bookmarking is just a flag), but
    // toggling it — or editing the note — both bump lastUpdated, which is what "the quote I most
    // recently touched" actually means. The endpoint's default sort is dateCreated desc, which
    // surfaced whichever bookmarked quote was created earliest-most-recently — e.g. a 2023 note —
    // ignoring every edit made since. Sort by lastUpdated instead.
    queryFn: () => listEntities('notes', { typeId, bookmarked: true, size: 1, sort: 'lastUpdated,desc' }),
    enabled: typeId != null,
    // The global default (30s stale, no refetch-on-focus) is right for the rest of the app, but
    // this is meant to reflect whichever note is currently bookmarked — both in the builder's live
    // preview and on a dashboard left open on a TV — so always treat it as stale.
    staleTime: 0,
    refetchInterval: 2 * 60 * 1000,
  });
  const note = data?.content?.[0];

  if (!typeCode) {
    return <Typography color="text.secondary">No note type configured.</Typography>;
  }
  if (!note) {
    return <Typography color="text.secondary">No bookmarked "{typeCode}" note found.</Typography>;
  }
  return <QuoteText note={note} />;
}

function QuoteText({ note }: { note: EntityRecord }) {
  const text = (note.description as string) || note.displayTitle;
  const rtl = dirOf(text) === 'rtl';
  return (
    <Box sx={{ px: 0.5 }}>
      <Typography
        variant="body1"
        dir={rtl ? 'rtl' : 'ltr'}
        sx={{ fontStyle: 'italic', lineHeight: 1.4, whiteSpace: 'pre-wrap', textAlign: 'justify' }}
      >
        “{text}”
      </Typography>
    </Box>
  );
}

/** Public (unauthenticated) variant — note already resolved server-side, no query. */
function PublicQuoteComponent({ note }: { note: EntityRecord | null }) {
  if (!note) {
    return <Typography color="text.secondary">No bookmarked note found.</Typography>;
  }
  return <QuoteText note={note} />;
}

// ---- Calendar: a real week time-grid (column per day, hour rows), not a list -------

interface CalendarConfig {
  eventType?: string;
  startHour?: number;
  endHour?: number;
}

const GRID_GUTTER = 46;
const HEADER_H = 28;

function CalendarComponent({ config }: { config?: string }) {
  const { eventType, startHour, endHour } = safeParse<CalendarConfig>(config, {});
  const days = useCurrentWeek();

  const { data } = useQuery({
    queryKey: ['dashboard-calendar', eventType, ymd(days[0]), ymd(days[6])],
    queryFn: () => getCalendarEvents(ymd(days[0]), ymd(days[6]), 'journals,planners', eventType),
    staleTime: 0,
    refetchInterval: 2 * 60 * 1000,
  });

  return (
    <CalendarGrid
      eventType={eventType}
      startHour={startHour}
      endHour={endHour}
      days={days}
      events={data ?? []}
      navigable
    />
  );
}

/** Public (unauthenticated) variant — events already resolved server-side for the current week. */
function PublicCalendarComponent({ config, events }: { config?: string; events: CalendarEvent[] }) {
  const { eventType, startHour, endHour } = safeParse<CalendarConfig>(config, {});
  const days = useCurrentWeek();
  return <CalendarGrid eventType={eventType} startHour={startHour} endHour={endHour} days={days} events={events} />;
}

function useCurrentWeek(): Date[] {
  return useMemo(() => {
    const now = new Date();
    const day = now.getDay(); // 0=Sun
    const monday = new Date(now);
    monday.setDate(now.getDate() - (day === 0 ? 6 : day - 1));
    monday.setHours(0, 0, 0, 0);
    return Array.from({ length: 7 }, (_, i) => {
      const d = new Date(monday);
      d.setDate(monday.getDate() + i);
      return d;
    });
  }, []);
}

function CalendarGrid({
  eventType,
  startHour,
  endHour,
  days,
  events,
  navigable,
}: {
  eventType?: string;
  startHour?: number;
  endHour?: number;
  days: Date[];
  events: CalendarEvent[];
  navigable?: boolean;
}) {
  const navigate = useNavigate();
  const from = startHour != null && startHour >= 0 && startHour < 24 ? startHour : 7;
  const to = endHour != null && endHour > from && endHour <= 24 ? endHour : 22;
  const rows = to - from;
  const todayYmd = ymd(new Date());

  // Rows are sized as a percentage of the available height (not a fixed px), so the whole grid
  // stretches to fill the space the dashboard grid gives this region instead of scrolling.
  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', minHeight: 0 }}>
      <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1, flexShrink: 0 }}>
        {eventType ? `${eventType} calendar` : 'Calendar'}
      </Typography>
      <Box sx={{ flex: 1, display: 'flex', minHeight: 0 }}>
        <Box sx={{ width: GRID_GUTTER, flexShrink: 0, display: 'flex', flexDirection: 'column' }}>
          <Box sx={{ height: HEADER_H, flexShrink: 0 }} />
          <Box sx={{ flex: 1, position: 'relative' }}>
            {Array.from({ length: rows }, (_, i) => (
              <Typography
                key={i}
                variant="body2"
                color="text.secondary"
                sx={{
                  position: 'absolute',
                  top: `${(i / rows) * 100}%`,
                  right: 6,
                  fontSize: 13,
                  lineHeight: 1,
                  transform: 'translateY(-50%)',
                  whiteSpace: 'nowrap',
                }}
              >
                {String(from + i).padStart(2, '0')}:00
              </Typography>
            ))}
          </Box>
        </Box>
        {days.map((day) => {
          const dKey = ymd(day);
          return (
            <Box key={dKey} sx={{ flex: 1, minWidth: 60, display: 'flex', flexDirection: 'column' }}>
              <Typography
                variant="body2"
                align="center"
                sx={{
                  height: HEADER_H,
                  flexShrink: 0,
                  fontSize: 13,
                  fontWeight: dKey === todayYmd ? 700 : 400,
                  color: dKey === todayYmd ? 'primary.main' : undefined,
                }}
              >
                {day.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric' })}
              </Typography>
              <Box
                sx={{
                  flex: 1,
                  position: 'relative',
                  borderLeft: 1,
                  borderColor: 'divider',
                  bgcolor: dKey === todayYmd ? 'action.hover' : undefined,
                }}
              >
                {Array.from({ length: rows }, (_, i) => (
                  <Box
                    key={i}
                    sx={{
                      position: 'absolute',
                      top: `${(i / rows) * 100}%`,
                      left: 0,
                      right: 0,
                      borderTop: 1,
                      borderColor: 'grey.100',
                    }}
                  />
                ))}
                {events
                  .filter((e) => e.start.slice(0, 10) === dKey)
                  .map((e) => (
                    <GridEventBlock
                      key={`${e.module}-${e.id}`}
                      ev={e}
                      startHour={from}
                      rows={rows}
                      onClick={navigable ? () => navigate(`/${e.module}`) : undefined}
                    />
                  ))}
              </Box>
            </Box>
          );
        })}
      </Box>
    </Box>
  );
}

/** One event, positioned within its day column by time-of-day (as a % of the column's own height,
 * so it stays correctly placed as the grid stretches) — coloured by the event's own type (journal
 * and planner each keep their own type lookup, so the same name can carry a different colour per
 * module). */
function GridEventBlock({
  ev,
  startHour,
  rows,
  onClick,
}: {
  ev: CalendarEvent;
  startHour: number;
  rows: number;
  onClick?: () => void;
}) {
  const start = new Date(ev.start);
  const end = ev.end ? new Date(ev.end) : new Date(start.getTime() + 30 * 60_000);
  const minutesFromStart = start.getHours() * 60 + start.getMinutes() - startHour * 60;
  const durationMin = Math.max(15, (end.getTime() - start.getTime()) / 60_000);
  const totalMinutes = rows * 60;
  const topPct = Math.max(0, (minutesFromStart / totalMinutes) * 100);
  const heightPct = Math.max(4, (durationMin / totalMinutes) * 100);
  const style = lookupStyle(
    ev.typeId != null ? { id: ev.typeId, name: ev.typeName ?? '', style: ev.typeStyle } : undefined,
  );
  const bg = style.backgroundColor ?? (ev.module === 'journals' ? '#8764b8' : '#0078d4');

  return (
    <Tooltip
      title={
        <Box dir="auto">
          <Typography variant="body2" sx={{ fontWeight: 700 }}>
            {ev.title || '(untitled)'} ({start.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })})
          </Typography>
          {ev.description && ev.description !== '?' && (
            <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
              {ev.description}
            </Typography>
          )}
        </Box>
      }
    >
      <Box
        onClick={onClick}
        dir="auto"
        sx={{
          position: 'absolute',
          top: `${topPct}%`,
          height: `${heightPct}%`,
          left: 1,
          right: 1,
          bgcolor: bg,
          color: '#fff',
          borderRadius: 0.5,
          px: 0.5,
          fontSize: 12,
          lineHeight: 1.3,
          overflow: 'hidden',
          cursor: onClick ? 'pointer' : 'default',
          '&:hover': onClick ? { filter: 'brightness(1.1)' } : undefined,
        }}
      >
        {ev.title || '(untitled)'}
      </Box>
    </Tooltip>
  );
}

// ---- Goals habit heatmap (goals flagged "dashboard") --------------------------------

function GoalsHeatmapComponent() {
  const { data } = useQuery({
    queryKey: ['list', 'goals', 'dashboard'],
    queryFn: () => listEntities('goals', { dashboard: true, size: 50 }),
    staleTime: 0,
    refetchInterval: 2 * 60 * 1000,
  });
  const goals = data?.content ?? [];
  if (goals.length === 0) {
    return (
      <Typography color="text.secondary" variant="body2">
        No goals flagged "Show habit heatmap on dashboards" yet.
      </Typography>
    );
  }
  return (
    <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap>
      {goals.map((g) => (
        <GoalHeatmapGroup key={g.id} goalId={g.id} goalCode={g.code as string | undefined} />
      ))}
    </Stack>
  );
}

function GoalHeatmapGroup({ goalId, goalCode }: { goalId: number; goalCode?: string }) {
  const { data } = useQuery({
    queryKey: ['indicators', 'goal', goalId],
    queryFn: () => listEntities('indicators', { goalId, size: 50 }),
    staleTime: 0,
    refetchInterval: 2 * 60 * 1000,
  });
  const indicators = data?.content ?? [];
  if (indicators.length === 0) return null;
  return (
    <GoalHeatmapGroupView goalId={goalId} goalCode={goalCode} indicators={indicators.map((i) => ({ indicator: i }))} />
  );
}

/** Public (unauthenticated) variant — goals + indicators + series already resolved server-side. */
function PublicGoalsHeatmapComponent({ groups }: { groups: PublicGoalHeatmap[] }) {
  const withIndicators = groups.filter((g) => g.indicators.length > 0);
  if (withIndicators.length === 0) {
    return (
      <Typography color="text.secondary" variant="body2">
        No goals flagged "Show habit heatmap on dashboards" yet.
      </Typography>
    );
  }
  return (
    <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap>
      {withIndicators.map((g) => (
        <GoalHeatmapGroupView
          key={g.goalId}
          goalId={g.goalId}
          goalCode={g.goalCode}
          indicators={g.indicators.map((iws) => ({ indicator: iws.indicator, series: iws.series }))}
        />
      ))}
    </Stack>
  );
}

function GoalHeatmapGroupView({
  goalId,
  goalCode,
  indicators,
}: {
  goalId: number;
  goalCode?: string;
  indicators: { indicator: EntityRecord; series?: SeriesPoint[] }[];
}) {
  return (
    <Box sx={{ width: 'fit-content' }}>
      {/* Each indicator card already shows the goal's own title (from how "Track this goal"
          names it) — labelling the group with the title again just repeats it. A short code (or
          the id, in red, when the goal has no code) tells the groups apart without the repeat. */}
      <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }} dir="auto">
        {goalCode ? goalCode : <Box component="span" sx={{ color: 'error.main' }}>#{goalId}</Box>}
      </Typography>
      <Stack spacing={1}>
        {indicators.map(({ indicator, series }) => (
          <HabitHeatmap key={indicator.id} indicator={indicator} readOnly preloadedSeries={series} />
        ))}
      </Stack>
    </Box>
  );
}

// ---- Kanban board (bookmarked goals + tasks, grouped by department) -----------------

function isDone(r: EntityRecord): boolean {
  return r.completedOn != null || r.status?.code === 'done';
}

function KanbanComponent() {
  const { data } = useQuery({
    queryKey: ['kanban'],
    queryFn: getKanban,
    staleTime: 0,
    refetchInterval: 2 * 60 * 1000,
  });
  return <KanbanBoard goals={data?.goals ?? []} tasks={data?.tasks ?? []} navigable />;
}

/** Public (unauthenticated) variant — kanban board data already resolved server-side. */
function PublicKanbanComponent({ data }: { data: KanbanData | null }) {
  return <KanbanBoard goals={data?.goals ?? []} tasks={data?.tasks ?? []} />;
}

function KanbanBoard({ goals, tasks, navigable }: { goals: EntityRecord[]; tasks: EntityRecord[]; navigable?: boolean }) {
  const navigate = useNavigate();

  const tasksByGoal = useMemo(() => {
    const shown = new Set(goals.map((g) => g.id));
    const map = new Map<number, EntityRecord[]>();
    for (const t of tasks) {
      if (t.goalId != null && shown.has(t.goalId)) {
        if (!map.has(t.goalId)) map.set(t.goalId, []);
        map.get(t.goalId)!.push(t);
      }
    }
    return map;
  }, [goals, tasks]);

  const doneSx = { textDecoration: 'line-through', opacity: 0.55 } as const;

  if (goals.length === 0 && tasks.length === 0) {
    return (
      <Typography color="text.secondary" variant="body2">
        No bookmarked goals or tasks.
      </Typography>
    );
  }

  return (
    <Stack spacing={1.5}>
      {goals.map((g) => (
        <Box key={g.id}>
          <RecordCard module="goals" record={g} onClick={navigable ? () => navigate('/goals') : undefined} />
          {(tasksByGoal.get(g.id) ?? []).length > 0 && (
            <Stack spacing={0.5} sx={{ mt: 0.5, ml: 1.5, pl: 1, borderLeft: 2, borderColor: 'divider' }}>
              {tasksByGoal.get(g.id)!.map((t) => (
                <RecordCard
                  key={t.id}
                  module="tasks"
                  record={t}
                  onClick={navigable ? () => navigate('/tasks') : undefined}
                  sx={isDone(t) ? doneSx : undefined}
                />
              ))}
            </Stack>
          )}
        </Box>
      ))}
    </Stack>
  );
}

// ---- File content (plain text, under repo 1/2/3) -------------------------------------

interface FileConfig {
  repo?: number;
  path?: string;
}

function FileComponent({ config }: { config?: string }) {
  const { repo, path } = safeParse<FileConfig>(config, {});
  const { data, isError } = useQuery({
    queryKey: ['dashboard-file', repo, path],
    queryFn: () => getDashboardFileContent(repo || 1, path!),
    enabled: Boolean(path),
    // Files can change on disk outside the app — keep it fresh for a screen left open unattended.
    staleTime: 0,
    refetchInterval: 5 * 60 * 1000,
  });

  if (!path) {
    return <Typography color="text.secondary">No file path configured.</Typography>;
  }
  if (isError) {
    return (
      <Typography color="error" variant="body2">
        Could not read {path}.
      </Typography>
    );
  }
  return <FileView content={data?.content} truncated={data?.truncated} />;
}

/** Public (unauthenticated) variant — file content already read server-side. */
function PublicFileComponent({ data }: { data: DashboardFileContent | null }) {
  if (!data) {
    return <Typography color="text.secondary">No file path configured.</Typography>;
  }
  return <FileView content={data.content} truncated={data.truncated} />;
}

function FileView({ content, truncated }: { content?: string; truncated?: boolean }) {
  return (
    <Box sx={{ maxHeight: 420, overflow: 'auto' }}>
      <Typography
        component="pre"
        variant="body2"
        dir="auto"
        sx={{ whiteSpace: 'pre-wrap', fontFamily: 'inherit', m: 0 }}
      >
        {content}
      </Typography>
      {truncated && <Chip size="small" label="truncated" sx={{ mt: 1 }} />}
    </Box>
  );
}

// ---- Public (unauthenticated) dispatcher — every region's data pre-resolved server-side ---------

/**
 * Renders one region using a fully-resolved {@link PublicDashboardRegion} — no queries of its own,
 * no auth. Backs the chrome-free dashboard view meant to be left open on a TV/kiosk with no login;
 * see PublicDashboardService on the backend for why this is one aggregate snapshot rather than the
 * generic per-module read endpoints opened up to anonymous callers.
 */
export function PublicDashboardComponent({ region }: { region: PublicDashboardRegion }) {
  switch (region.componentType) {
    case 'quote':
      return <PublicQuoteComponent note={region.quote} />;
    case 'calendar':
      return <PublicCalendarComponent config={region.config} events={region.calendarEvents ?? []} />;
    case 'goalsHeatmap':
      return <PublicGoalsHeatmapComponent groups={region.goalsHeatmap ?? []} />;
    case 'kanban':
      return <PublicKanbanComponent data={region.kanbanBoard} />;
    case 'file':
      return <PublicFileComponent data={region.file} />;
    default:
      return <Typography color="text.secondary">Unknown component type "{region.componentType}".</Typography>;
  }
}
