import { useMemo, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Box, Checkbox, Chip, Stack, Tooltip, Typography } from '@mui/material';
import Autorenew from '@mui/icons-material/Autorenew';
import {
  CalendarEvent,
  ChecklistMark,
  ChecklistWeek,
  getChecklistIndex,
  getChecklistWeek,
  DashboardFileContent,
  EntityRecord,
  KanbanData,
  Lookup,
  PublicDashboardRegion,
  PublicGoalHeatmap,
  SeriesPoint,
  getCalendarEvents,
  getDashboardFileContent,
  getKanban,
  getLocalNotes,
  getLookups,
  listEntities,
  LocalNote,
  setTaskCompleted,
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
  { type: 'checklist', label: 'Checklist (today)' },
  { type: 'tasks', label: 'Open tasks' },
  { type: 'localTasks', label: 'Local tasks (notes folder)' },
];

function safeParse<T>(json: string | undefined, fallback: T): T {
  if (!json) return fallback;
  try {
    return { ...fallback, ...JSON.parse(json) } as T;
  } catch {
    return fallback;
  }
}

/** Text-size choices for a region (its config's `textScale`, a percentage of the normal size). */
export const TEXT_SCALES = [70, 80, 90, 100, 115, 130, 150, 175, 200, 250];

/** What a region's text size is when it hasn't been set: a little under the app's normal size, so a
 * dashboard fits more on one screen. */
export const DEFAULT_TEXT_SCALE = 90;

/** A region's text size as a CSS zoom factor (DEFAULT_TEXT_SCALE when unset). Zoom scales the whole component
 * (text, chips, the calendar's hour labels) together, so it stays laid out as at 100 %. */
export function regionZoom(config?: string): number {
  const { textScale } = safeParse<{ textScale?: number }>(config, {});
  return (typeof textScale === 'number' && textScale >= 50 && textScale <= 400 ? textScale : DEFAULT_TEXT_SCALE) / 100;
}

/** "1,2,1" → [1,2,1], fitted to `columns` (missing or bad entries count as 1). */
export function parseColumnWidths(widths: string | null | undefined, columns: number): number[] {
  const parts = (widths ?? '').split(',').map((p) => Number(p.trim()));
  return Array.from({ length: columns }, (_, i) => (parts[i] > 0 ? parts[i] : 1));
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
    case 'checklist':
      return <ChecklistComponent />;
    case 'tasks':
      return <TasksComponent config={config} />;
    case 'localTasks':
      return <LocalTasksComponent config={config} />;
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
                {layoutDay(events.filter((e) => e.start.slice(0, 10) === dKey)).map(({ ev: e, lane, lanes }, i) => (
                  <GridEventBlock
                    // A virtual occurrence has no id: its series and slot tell it apart.
                    key={`${e.module}-${e.id ?? `s${e.seriesId}-${e.start}`}-${i}`}
                    ev={e}
                    startHour={from}
                    rows={rows}
                    lane={lane}
                    lanes={lanes}
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

function eventRange(ev: CalendarEvent): [number, number] {
  const start = new Date(ev.start).getTime();
  const end = ev.end ? new Date(ev.end).getTime() : start + 30 * 60_000;
  return [start, Math.max(end, start + 15 * 60_000)];
}

/** Side by side instead of on top of each other: events that overlap share the column's width,
 * each in its own lane (the usual calendar layout), so two things at the same hour both show. */
function layoutDay(events: CalendarEvent[]): { ev: CalendarEvent; lane: number; lanes: number }[] {
  const sorted = [...events].sort((a, b) => eventRange(a)[0] - eventRange(b)[0] || eventRange(b)[1] - eventRange(a)[1]);
  const out: { ev: CalendarEvent; lane: number; lanes: number }[] = [];
  let cluster: { ev: CalendarEvent; lane: number; lanes: number }[] = [];
  let laneEnds: number[] = [];
  let clusterEnd = -Infinity;
  const close = () => {
    for (const c of cluster) c.lanes = laneEnds.length;
    out.push(...cluster);
    cluster = [];
    laneEnds = [];
  };
  for (const ev of sorted) {
    const [s, e] = eventRange(ev);
    if (s >= clusterEnd && cluster.length) close();
    let lane = laneEnds.findIndex((end) => end <= s);
    if (lane < 0) {
      lane = laneEnds.length;
      laneEnds.push(e);
    } else {
      laneEnds[lane] = e;
    }
    cluster.push({ ev, lane, lanes: 1 });
    clusterEnd = Math.max(clusterEnd === -Infinity ? e : clusterEnd, e);
  }
  if (cluster.length) close();
  return out;
}

/** One event, positioned within its day column by time-of-day (as a % of the column's own height,
 * so it stays correctly placed as the grid stretches) — coloured by the event's own type (journal
 * and planner each keep their own type lookup, so the same name can carry a different colour per
 * module). */
function GridEventBlock({
  ev,
  startHour,
  rows,
  lane = 0,
  lanes = 1,
  onClick,
}: {
  ev: CalendarEvent;
  startHour: number;
  rows: number;
  lane?: number;
  lanes?: number;
  onClick?: () => void;
}) {
  const start = new Date(ev.start);
  const [, endMs] = eventRange(ev);
  const totalMinutes = rows * 60;
  const minutesFromStart = start.getHours() * 60 + start.getMinutes() - startHour * 60;
  const durationMin = (endMs - start.getTime()) / 60_000;
  // Kept inside the shown hours: an event before them sits at the top, one after at the bottom.
  const heightPct = Math.min(100, Math.max(4, (Math.min(durationMin, totalMinutes) / totalMinutes) * 100));
  const topPct = Math.min(100 - heightPct, Math.max(0, (minutesFromStart / totalMinutes) * 100));
  const recurring = ev.virtual || ev.seriesId != null;
  const moduleColor = ev.module === 'journals' ? '#8764b8' : '#0078d4';
  const style = lookupStyle(
    ev.typeId != null ? { id: ev.typeId, name: ev.typeName ?? '', style: ev.typeStyle } : undefined,
  );
  const bg = style.backgroundColor ?? (ev.module === 'journals' ? '#8764b8' : '#0078d4');
  // A type colour is a pastel: dark text on it, as on the calendar page (white on it was unreadable).
  const fg = style.backgroundColor ? (style.color ?? 'rgba(0,0,0,0.85)') : '#fff';

  return (
    <Tooltip
      title={
        <Box dir="auto">
          <Typography variant="body2" sx={{ fontWeight: 700 }}>
            {ev.title || '(untitled)'} ({start.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })})
          </Typography>
          <Typography variant="caption" sx={{ display: 'block', opacity: 0.85 }}>
            {ev.module === 'journals' ? 'Journal' : 'Planner'}
            {ev.typeName ? ` · ${ev.typeName}` : ''}
            {recurring ? ' · repeats' : ''}
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
          left: `calc(${(lane / lanes) * 100}% + 1px)`,
          width: `calc(${100 / lanes}% - 2px)`,
          boxSizing: 'border-box',
          bgcolor: bg,
          color: fg,
          border: 1,
          borderColor: 'rgba(0,0,0,0.25)',
          // The module shows on the left edge: purple for a journal entry, blue for a planner one.
          borderLeft: `4px solid ${moduleColor}`,
          borderRadius: 0.5,
          px: 0.5,
          fontSize: 12,
          lineHeight: 1.3,
          overflow: 'hidden',
          cursor: onClick ? 'pointer' : 'default',
          '&:hover': onClick ? { filter: 'brightness(1.1)' } : undefined,
        }}
      >
        {recurring && (
          <Autorenew
            aria-label="repeats"
            sx={{ fontSize: '1.1em', verticalAlign: '-0.2em', mr: 0.25, opacity: 0.8 }}
          />
        )}
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
    case 'checklist':
      return <ChecklistView week={region.checklist ?? null} />;
    case 'tasks':
      return <TasksView tasks={region.tasks ?? []} />;
    case 'localTasks':
      return <LocalTasksView notes={region.localTasks ?? []} minPriority={localTasksConfig(region.config).minPriority} />;
    default:
      return <Typography color="text.secondary">Unknown component type "{region.componentType}".</Typography>;
  }
}

// ---- Checklist: today's column of this week's phone checklist ----------------------

function ChecklistComponent() {
  const { data } = useQuery({
    queryKey: ['dashboard-checklist'],
    queryFn: async () => {
      const idx = await getChecklistIndex();
      try {
        return await getChecklistWeek(idx.current);
      } catch {
        return null;
      }
    },
    staleTime: 0,
    refetchInterval: 2 * 60 * 1000,
  });
  return <ChecklistView week={data ?? null} />;
}

const MARK_GLYPH: Record<ChecklistMark, string> = { DONE: '✓', PARTIAL: '◐', NONE: '' };

/** Today's state of every item: daily groups show today's mark, weekly groups the week's. Done
 * items are dimmed and struck through, so what is left reads first from across a room. */
function ChecklistView({ week }: { week: ChecklistWeek | null }) {
  const dayIndex = (new Date().getDay() + 6) % 7; // Mon=0
  if (!week) {
    return (
      <>
        <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>
          Checklist
        </Typography>
        <Typography color="text.secondary">No checklist for this week yet.</Typography>
      </>
    );
  }
  const items = week.groups.flatMap((g) => g.items.map((i) => (g.weekly ? i.marks[0] : i.marks[dayIndex]) ?? 'NONE'));
  const done = items.filter((m) => m === 'DONE').length;
  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', minHeight: 0 }}>
      <Typography variant="subtitle1" sx={{ fontWeight: 700, flexShrink: 0 }}>
        Checklist
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1, flexShrink: 0 }}>
        {new Date().toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'short' })} · {done}/{items.length}
      </Typography>
      <Box sx={{ flex: 1, overflow: 'auto', minHeight: 0 }}>
        {week.groups.map((g) => (
          <Box key={g.code} sx={{ mb: 1.5 }}>
            <Typography variant="body2" sx={{ fontWeight: 700, color: 'primary.main', mb: 0.5 }}>
              {g.title}
              {g.weekly ? ' (this week)' : ''}
            </Typography>
            {g.items.map((i) => {
              const m = (g.weekly ? i.marks[0] : i.marks[dayIndex]) ?? 'NONE';
              return (
                <Stack key={i.code} direction="row" spacing={1} alignItems="flex-start" sx={{ py: 0.25 }}>
                  <Box
                    sx={{
                      width: 20,
                      height: 20,
                      flexShrink: 0,
                      border: 1.5,
                      borderRadius: 0.5,
                      borderColor: m === 'NONE' ? 'text.secondary' : 'success.main',
                      color: 'success.main',
                      fontSize: 15,
                      lineHeight: '18px',
                      textAlign: 'center',
                      fontWeight: 700,
                    }}
                  >
                    {MARK_GLYPH[m]}
                  </Box>
                  <Typography
                    dir={dirOf(i.text)}
                    sx={{
                      fontSize: 15,
                      lineHeight: 1.35,
                      color: m === 'DONE' ? 'text.disabled' : 'text.primary',
                      textDecoration: m === 'DONE' ? 'line-through' : undefined,
                      textAlign: 'start',
                    }}
                  >
                    {i.text}
                  </Typography>
                </Stack>
              );
            })}
          </Box>
        ))}
      </Box>
    </Box>
  );
}

// ---- Open tasks: active status, due ones first ---------------------------------------

interface TasksConfig {
  limit?: number;
  /** Leave out tasks overdue by more than this many days (default 30; 0 keeps them all). */
  overdueDays?: number;
  bookmarkedOnly?: boolean;
}

/** Same choice as the server makes for the TV view (PublicDashboardService.resolveTasks). */
function pickOpenTasks(rows: EntityRecord[], { limit, overdueDays }: TasksConfig): EntityRecord[] {
  const days = overdueDays != null && overdueDays >= 0 ? overdueDays : 30;
  const cutoff = new Date();
  cutoff.setDate(cutoff.getDate() - days);
  const cutoffYmd = ymd(cutoff);
  const kept = rows.filter((t) => {
    if (t.completedOn) return false;
    const due = (t.endDate as string | undefined)?.slice(0, 10);
    return days === 0 || !due || due >= cutoffYmd;
  });
  // Stable sort: rows arrive most recently updated first, which breaks the remaining ties.
  kept.sort((a, b) => {
    const ae = (a.endDate as string | undefined) ?? '';
    const be = (b.endDate as string | undefined) ?? '';
    if (ae !== be) return !ae ? 1 : !be ? -1 : ae.localeCompare(be);
    return ((b.priority as number | undefined) ?? -1) - ((a.priority as number | undefined) ?? -1);
  });
  return kept.slice(0, limit && limit > 0 ? limit : 30);
}

function TasksComponent({ config }: { config?: string }) {
  const cfg = safeParse<TasksConfig>(config, {});
  const { data } = useQuery({
    queryKey: ['dashboard-tasks', cfg.bookmarkedOnly ?? false],
    queryFn: () =>
      listEntities('tasks', {
        activeStatus: true,
        ...(cfg.bookmarkedOnly ? { bookmarked: true } : {}),
        size: 2000,
        sort: 'lastUpdated,desc',
      }),
    staleTime: 0,
    refetchInterval: 2 * 60 * 1000,
  });
  const sorted = useMemo(
    () => pickOpenTasks([...(data?.content ?? [])], cfg),
    [data, cfg.limit, cfg.overdueDays],
  );
  return <TasksView tasks={sorted} />;
}

/** What the list holds, said on the list itself (a TV has no tooltips to hover). */
const OPEN_TASKS_NOTE =
  'Open: not completed, with an active status (or none). Due first, soonest first; older than the overdue limit left out.';

function TasksView({ tasks }: { tasks: EntityRecord[] }) {
  const today = ymd(new Date());
  const qc = useQueryClient();
  // Ticked here, by task id: stays struck through until the next refresh drops it from the list,
  // so a wrong tick can be undone where it was made.
  const [ticked, setTicked] = useState<Record<string, boolean>>({});
  const [note, setNote] = useState<string | null>(null);

  const toggle = async (id: number, done: boolean) => {
    setTicked((m) => ({ ...m, [id]: done }));
    const result = await setTaskCompleted(id, done);
    if (result !== 'ok') {
      setTicked((m) => ({ ...m, [id]: !done }));
      setNote(
        result === 'no-session'
          ? 'Sign in on this screen (with an account that can edit) to tick tasks off.'
          : 'Could not save — try again.',
      );
      window.setTimeout(() => setNote(null), 6000);
      return;
    }
    qc.invalidateQueries({ queryKey: ['list', 'tasks'] });
    qc.invalidateQueries({ queryKey: ['record', 'tasks', id] });
  };

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', minHeight: 0 }}>
      <Typography variant="subtitle1" sx={{ fontWeight: 700, flexShrink: 0 }}>
        Open tasks
      </Typography>
      <Typography variant="caption" color="text.secondary" sx={{ mb: 1, flexShrink: 0, lineHeight: 1.3 }}>
        {OPEN_TASKS_NOTE}
      </Typography>
      {note && (
        <Typography variant="body2" color="warning.dark" sx={{ mb: 1, flexShrink: 0 }}>
          {note}
        </Typography>
      )}
      {tasks.length === 0 && <Typography color="text.secondary">No open tasks.</Typography>}
      <Box sx={{ flex: 1, overflow: 'auto', minHeight: 0 }}>
        {tasks.map((t) => {
          const due = (t.endDate as string | undefined)?.slice(0, 10);
          const late = !!due && due < today;
          const title = String(t.summary ?? t.displayTitle ?? '');
          const status = t.status as Lookup | undefined;
          const done = ticked[t.id] ?? false;
          return (
            <Stack
              key={String(t.id)}
              direction="row"
              spacing={0.5}
              alignItems="flex-start"
              sx={{ py: 0.5, borderBottom: 1, borderColor: 'divider' }}
            >
              <Tooltip title={done ? 'Completed — untick to reopen' : 'Mark as completed'}>
                <Checkbox
                  size="small"
                  checked={done}
                  onChange={(e) => toggle(t.id, e.target.checked)}
                  inputProps={{ 'aria-label': `Mark "${title}" as completed` }}
                  sx={{ p: 0.25, mt: 0.1 }}
                />
              </Tooltip>
              <Box sx={{ minWidth: 0, flex: 1, opacity: done ? 0.5 : 1 }}>
                <Typography
                  dir={dirOf(title)}
                  sx={{
                    fontSize: 15,
                    lineHeight: 1.35,
                    textAlign: 'start',
                    textDecoration: done ? 'line-through' : undefined,
                  }}
                >
                  {title || '(untitled)'}
                </Typography>
                <Stack direction="row" spacing={0.75} alignItems="center" sx={{ mt: 0.25, flexWrap: 'wrap' }}>
                  {due && (
                    <Typography variant="body2" sx={{ color: late ? 'error.main' : due === today ? 'warning.dark' : 'text.secondary', fontWeight: late || due === today ? 700 : 400 }}>
                      {due === today ? 'Today' : new Date(due + 'T00:00').toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })}
                    </Typography>
                  )}
                  {t.priority != null && (
                    <Typography variant="body2" color="text.secondary">
                      P{String(t.priority)}
                    </Typography>
                  )}
                  {status?.name && (
                    <Chip size="small" label={status.name} sx={{ height: 20, fontSize: 12, ...lookupStyle(status) }} />
                  )}
                </Stack>
              </Box>
            </Stack>
          );
        })}
      </Box>
    </Box>
  );
}

// ---- Local tasks: task notes (type T) in the local notes folder ---------------------

interface LocalTasksConfig {
  /** Lowest priority shown (default 3). */
  minPriority: number;
  limit: number;
}

function localTasksConfig(config?: string): LocalTasksConfig {
  const c = safeParse<Partial<LocalTasksConfig>>(config, {});
  return {
    minPriority: c.minPriority && c.minPriority > 0 ? c.minPriority : 3,
    limit: c.limit && c.limit > 0 ? c.limit : 30,
  };
}

/** Same choice as the server makes for the TV view (PublicDashboardService.resolveLocalTasks). */
function LocalTasksComponent({ config }: { config?: string }) {
  const { minPriority, limit } = localTasksConfig(config);
  const { data } = useQuery({
    queryKey: ['local-notes'],
    queryFn: getLocalNotes,
    staleTime: 0,
    refetchInterval: 2 * 60 * 1000,
  });
  const notes = useMemo(
    () =>
      (data?.days ?? [])
        .flatMap((d) => d.notes)
        .filter((n) => n.type?.toUpperCase() === 'T' && (n.priority ?? 0) >= minPriority)
        .sort((a, b) => (b.priority ?? 0) - (a.priority ?? 0) || b.timestamp.localeCompare(a.timestamp))
        .slice(0, limit),
    [data, minPriority, limit],
  );
  return <LocalTasksView notes={notes} minPriority={minPriority} />;
}

function LocalTasksView({ notes, minPriority }: { notes: LocalNote[]; minPriority: number }) {
  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', minHeight: 0 }}>
      <Typography variant="subtitle1" sx={{ fontWeight: 700, flexShrink: 0 }}>
        Local tasks
      </Typography>
      <Typography variant="caption" color="text.secondary" sx={{ mb: 1, flexShrink: 0, lineHeight: 1.3 }}>
        Task notes (T) in the local notes folder, priority {minPriority} and up, highest first.
      </Typography>
      {notes.length === 0 && <Typography color="text.secondary">No task notes at that priority.</Typography>}
      <Box sx={{ flex: 1, overflow: 'auto', minHeight: 0 }}>
        {notes.map((n) => {
          const title = n.title || n.snippet || n.filename;
          return (
            <Box key={n.filename} sx={{ py: 0.5, borderBottom: 1, borderColor: 'divider' }}>
              <Typography dir={dirOf(title)} sx={{ fontSize: 15, lineHeight: 1.35, textAlign: 'start' }}>
                {title}
              </Typography>
              <Stack direction="row" spacing={0.75} alignItems="center" sx={{ mt: 0.25, flexWrap: 'wrap' }}>
                <Typography variant="body2" color="text.secondary">
                  P{n.priority}
                </Typography>
                {n.deptName && (
                  <Typography variant="body2" color="text.secondary">
                    {n.deptName}
                  </Typography>
                )}
                {n.primaryTag && <Chip size="small" label={`#${n.primaryTag}`} sx={{ height: 20, fontSize: 12 }} />}
                <Typography variant="body2" color="text.secondary">
                  {new Date(n.timestamp).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })}
                </Typography>
              </Stack>
            </Box>
          );
        })}
      </Box>
    </Box>
  );
}
