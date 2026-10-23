import { useNavigate } from 'react-router-dom';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  addDays,
  addWeeks,
  format,
  getISOWeek,
  isSameDay,
  parseISO,
  startOfWeek,
} from 'date-fns';
import {
  Box,
  Button,
  IconButton,
  MenuItem,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
import ChevronLeft from '@mui/icons-material/ChevronLeft';
import ChevronRight from '@mui/icons-material/ChevronRight';
import { DateCalendar } from '@mui/x-date-pickers/DateCalendar';
import {
  CalendarEvent,
  getCalendarEvents,
  getEntity,
  materialiseOccurrence,
  skipOccurrence,
  updateEntity,
} from '../api/client';
import { lookupStyle } from '../components/statusStyle';
import { SideNav } from '../components/SideNav';
import { EventDialog } from '../components/EventDialog';
import { useAuth } from '../hooks/useAuth';
import { useCalendarPrefs } from '../hooks/useCalendarPrefs';

const SLOT_PX = 26;
const GUTTER = 56;
/** Fallback block colour when an entry has no type (or its type has no configured colour). */
const MODULE_COLOR: Record<string, string> = {
  planners: '#0078d4',
  journals: '#8764b8',
  // Sprints are a third kind of thing on the same grid — a committed block of work rather than an
  // entry — so they get their own colour and their own border style (see eventStyle).
  sprints: '#107c41',
};

/**
 * An event's block styling. Colour comes from the entry's own TYPE (the same chip colour configured
 * on the Parameters page), so the calendar reads by category rather than by module. Journal and
 * planner types are separate lookups that can share a name/colour, so module identity is carried by
 * a second, non-colour channel instead: planners get a dashed border, journals a solid one, and
 * sprints a doubled one. Sprints have no type lookup, so they always take their module colour.
 */
function eventStyle(ev: CalendarEvent) {
  const typed = ev.typeName ? lookupStyle({ id: ev.typeId ?? 0, name: ev.typeName, style: ev.typeStyle }) : {};
  const bg = typed.backgroundColor ?? MODULE_COLOR[ev.module] ?? 'primary.main';
  return {
    bgcolor: bg,
    // Type colours are pastels meant for chips, so ink on them rather than the white used for the
    // saturated module fallback.
    color: typed.backgroundColor ? (typed.color ?? 'rgba(0,0,0,0.82)') : '#fff',
    border: '2px',
    borderStyle: ev.module === 'planners' ? 'dashed' : ev.module === 'sprints' ? 'double' : 'solid',
    borderColor: 'rgba(0,0,0,0.38)',
  };
}

function ymd(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
    d.getDate(),
  ).padStart(2, '0')}`;
}

/** Local (no-timezone-shift) "yyyy-MM-ddTHH:mm:ss" — matches EventDialog's convention so the
 *  server (which stores LocalDateTime) gets exactly the wall-clock time shown on the grid. */
function toLocalDateTimeString(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(
    d.getMinutes(),
  )}:00`;
}

/**
 * A stable React key / identity for an event. A virtual occurrence has no id — every one of them
 * would key as `planners-null` — so the series slot stands in for it.
 */
function eventKey(ev: CalendarEvent): string {
  return ev.id != null ? `${ev.module}-${ev.id}` : `${ev.module}-s${ev.seriesId}-${ev.recurrenceId}`;
}

/**
 * The record id behind an event, materialising the occurrence first if it doesn't have one yet.
 * This is the single place the "materialise on touch" rule is applied on the client: everything
 * that needs an id — opening, resizing — goes through here, so a recurring habit costs one row for
 * the rule plus one per occurrence actually acted on. The call is idempotent server-side, so no
 * bookkeeping is needed about what has already been materialised.
 */
async function resolveId(ev: CalendarEvent): Promise<number | null> {
  if (ev.id != null) return ev.id;
  if (ev.seriesId == null || !ev.recurrenceId) return null;
  const row = await materialiseOccurrence(ev.seriesId, ev.recurrenceId);
  return row.id;
}

/** An in-progress edge-drag on an existing event: which edge, and the live (snapped) preview. */
interface ResizeState {
  ev: CalendarEvent;
  day: Date;
  edge: 'start' | 'end';
  startClientY: number;
  origStartMin: number; // minutes-of-day, at drag start
  origEndMin: number;
  previewStartMin: number; // minutes-of-day, live during drag
  previewEndMin: number;
  moved: boolean; // true once the pointer has actually moved — suppresses the click-to-open
}

export function CalendarPage() {
  const qc = useQueryClient();
  const { canWriteContent } = useAuth();
  const navigate = useNavigate();
  const { prefs, update } = useCalendarPrefs();
  const [anchor, setAnchor] = useState<Date>(new Date());
  const [slot, setSlot] = useState<{ start: Date; end: Date } | null>(null);
  // Ticks the "now" line (and the today header highlight) forward without a page reload.
  const [now, setNow] = useState<Date>(new Date());
  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 30_000);
    return () => window.clearInterval(id);
  }, []);
  // Click-drag selection across adjacent slots in one day column.
  const [drag, setDrag] = useState<{ day: Date; startRow: number; endRow: number } | null>(null);
  // Edge-drag resize of an existing event (see ResizeState above).
  const [resize, setResize] = useState<ResizeState | null>(null);
  const resizeRef = useRef<ResizeState | null>(null);

  const days = useMemo<Date[]>(() => {
    if (prefs.view === 'day') return [anchor];
    const monday = startOfWeek(anchor, { weekStartsOn: 1 });
    return Array.from({ length: 7 }, (_, i) => addDays(monday, i));
  }, [anchor, prefs.view]);

  const rangeFrom = days[0];
  const rangeTo = days[days.length - 1];

  const { data } = useQuery({
    queryKey: ['calendar-events', ymd(rangeFrom), ymd(rangeTo)],
    queryFn: () => getCalendarEvents(ymd(rangeFrom), ymd(rangeTo)),
  });
  const events = data ?? [];

  const rows = Math.max(1, ((prefs.endHour - prefs.startHour) * 60) / prefs.slotMinutes);
  const colHeight = rows * SLOT_PX;

  const navStep = (dir: 1 | -1) =>
    setAnchor((a) => (prefs.view === 'day' ? addDays(a, dir) : addWeeks(a, dir)));

  // Open the event dialog for rows [loRow, hiRowExclusive) of a day — a single slot is one row.
  const openRange = useCallback(
    (day: Date, loRow: number, hiRowExclusive: number) => {
      if (!canWriteContent) return; // read-only users can't create calendar entries
      const startMin = prefs.startHour * 60 + loRow * prefs.slotMinutes;
      const endMin = prefs.startHour * 60 + hiRowExclusive * prefs.slotMinutes;
      const start = new Date(day);
      start.setHours(Math.floor(startMin / 60), startMin % 60, 0, 0);
      const end = new Date(day);
      end.setHours(Math.floor(endMin / 60), endMin % 60, 0, 0);
      setSlot({ start, end });
    },
    [prefs.startHour, prefs.slotMinutes, canWriteContent],
  );

  // Persist a resized event's new start/end. The generic PUT applies every DTO field, so the
  // record is re-fetched first and only startDate/endDate are changed on top of it (same pattern
  // DetailPane uses for its star/urgent toggles) — a partial body would null out everything else.
  //
  // A virtual occurrence has no record to PUT, so resizing one materialises it first: dragging an
  // occurrence to a different time is exactly the "touch" the model waits for.
  const commitResize = useCallback(
    async (r: ResizeState) => {
      const id = await resolveId(r.ev);
      if (id == null) return;
      const full = await getEntity(r.ev.module, id);
      const start = new Date(r.day);
      start.setHours(Math.floor(r.previewStartMin / 60), r.previewStartMin % 60, 0, 0);
      const end = new Date(r.day);
      end.setHours(Math.floor(r.previewEndMin / 60), r.previewEndMin % 60, 0, 0);
      await updateEntity(r.ev.module, id, {
        ...full,
        startDate: toLocalDateTimeString(start),
        endDate: toLocalDateTimeString(end),
      });
      qc.invalidateQueries({ queryKey: ['calendar-events'] });
    },
    [qc],
  );

  // Begin dragging an event's top (start) or bottom (end) edge. Mirrors AppShell's pane-resize
  // pattern: a single mousedown wires up its own window mousemove/mouseup pair (rather than an
  // effect keyed on drag state, which would tear down and re-add the listeners on every tick).
  const startEventResize = useCallback(
    (edge: 'start' | 'end', day: Date, ev: CalendarEvent, e: React.MouseEvent) => {
      if (!canWriteContent) return;
      e.preventDefault();
      e.stopPropagation();
      const start = parseISO(ev.start);
      const end = ev.end ? parseISO(ev.end) : new Date(start.getTime() + prefs.slotMinutes * 60_000);
      const origStartMin = start.getHours() * 60 + start.getMinutes();
      const origEndMin = end.getHours() * 60 + end.getMinutes();
      const startClientY = e.clientY;
      const dayStartMin = prefs.startHour * 60;
      const dayEndMin = prefs.endHour * 60;
      const state: ResizeState = {
        ev, day, edge, startClientY, origStartMin, origEndMin,
        previewStartMin: origStartMin,
        previewEndMin: origEndMin,
        moved: false,
      };
      resizeRef.current = state;
      setResize(state);

      const onMove = (ev2: MouseEvent) => {
        const r = resizeRef.current;
        if (!r) return;
        const deltaMin = Math.round((ev2.clientY - startClientY) / SLOT_PX) * prefs.slotMinutes;
        const next: ResizeState =
          edge === 'start'
            ? { ...r, previewStartMin: Math.min(Math.max(dayStartMin, origStartMin + deltaMin), origEndMin - prefs.slotMinutes), moved: true }
            : { ...r, previewEndMin: Math.max(Math.min(dayEndMin, origEndMin + deltaMin), origStartMin + prefs.slotMinutes), moved: true };
        resizeRef.current = next;
        setResize(next);
      };
      const onUp = () => {
        window.removeEventListener('mousemove', onMove);
        window.removeEventListener('mouseup', onUp);
        const r = resizeRef.current;
        resizeRef.current = null;
        setResize(null);
        if (r && r.moved) commitResize(r);
      };
      window.addEventListener('mousemove', onMove);
      window.addEventListener('mouseup', onUp);
    },
    [canWriteContent, prefs.slotMinutes, prefs.startHour, prefs.endHour, commitResize],
  );

  // Finish a drag on mouse-up (anywhere): turn the selected row span into a new event.
  useEffect(() => {
    if (!drag) return;
    const onUp = () => {
      setDrag((d) => {
        if (d) {
          const lo = Math.min(d.startRow, d.endRow);
          const hi = Math.max(d.startRow, d.endRow);
          openRange(d.day, lo, hi + 1);
        }
        return null;
      });
    };
    window.addEventListener('mouseup', onUp);
    return () => window.removeEventListener('mouseup', onUp);
  }, [drag, openRange]);

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <SideNav />
      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {/* Toolbar */}
        <Stack
          direction="row"
          spacing={1.5}
          alignItems="center"
          sx={{ p: 1.5, borderBottom: 1, borderColor: 'divider', flexWrap: 'wrap' }}
          useFlexGap
        >
          <IconButton onClick={() => navStep(-1)} size="small">
            <ChevronLeft />
          </IconButton>
          <Button size="small" onClick={() => setAnchor(new Date())}>
            Today
          </Button>
          <IconButton onClick={() => navStep(1)} size="small">
            <ChevronRight />
          </IconButton>
          <Typography variant="h6" sx={{ fontWeight: 700, minWidth: 220 }}>
            {format(rangeFrom, 'MMM d')} – {format(rangeTo, 'MMM d, yyyy')}
            <Typography component="span" variant="body2" color="text.secondary" sx={{ ml: 1 }}>
              · ISO week {getISOWeek(rangeFrom)}
            </Typography>
          </Typography>

          <Box sx={{ flex: 1 }} />

          <ToggleButtonGroup
            size="small"
            exclusive
            value={prefs.view}
            onChange={(_, v) => v && update({ view: v })}
          >
            <ToggleButton value="week">Week</ToggleButton>
            <ToggleButton value="day">Day</ToggleButton>
          </ToggleButtonGroup>

          <TextField
            select
            size="small"
            label="Slot"
            value={prefs.slotMinutes}
            onChange={(e) => update({ slotMinutes: Number(e.target.value) as 15 | 30 | 60 })}
            sx={{ width: 90 }}
          >
            <MenuItem value={15}>15 min</MenuItem>
            <MenuItem value={30}>30 min</MenuItem>
            <MenuItem value={60}>60 min</MenuItem>
          </TextField>
          <TextField
            select
            size="small"
            label="From"
            value={prefs.startHour}
            onChange={(e) => update({ startHour: Number(e.target.value) })}
            sx={{ width: 90 }}
          >
            {Array.from({ length: 24 }, (_, h) => (
              <MenuItem key={h} value={h} disabled={h >= prefs.endHour}>
                {String(h).padStart(2, '0')}:00
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            size="small"
            label="To"
            value={prefs.endHour}
            onChange={(e) => update({ endHour: Number(e.target.value) })}
            sx={{ width: 90 }}
          >
            {Array.from({ length: 24 }, (_, h) => h + 1).map((h) => (
              <MenuItem key={h} value={h} disabled={h <= prefs.startHour}>
                {String(h).padStart(2, '0')}:00
              </MenuItem>
            ))}
          </TextField>
        </Stack>

        {/* Grid + overview */}
        <Box sx={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
          <Box sx={{ flex: 1, overflow: 'auto' }}>
            {/* Day headers */}
            <Box sx={{ display: 'flex', position: 'sticky', top: 0, bgcolor: 'background.paper', zIndex: 2 }}>
              <Box sx={{ width: GUTTER, flexShrink: 0 }} />
              {days.map((d) => (
                <Box
                  key={d.toISOString()}
                  sx={{
                    flex: 1,
                    textAlign: 'center',
                    py: 1,
                    borderBottom: 1,
                    borderLeft: 1,
                    borderColor: 'divider',
                    bgcolor: isSameDay(d, now) ? 'action.hover' : undefined,
                  }}
                >
                  <Typography variant="caption" color="text.secondary">
                    {format(d, 'EEE')}
                  </Typography>
                  <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                    {format(d, 'd')}
                  </Typography>
                </Box>
              ))}
            </Box>

            {/* Time grid (pt leaves room so the first hour label isn't clipped) */}
            <Box sx={{ display: 'flex', pt: '9px' }}>
              {/* Time gutter */}
              <Box sx={{ width: GUTTER, flexShrink: 0 }}>
                {Array.from({ length: rows }, (_, i) => {
                  const mins = prefs.startHour * 60 + i * prefs.slotMinutes;
                  const onHour = mins % 60 === 0;
                  return (
                    <Box
                      key={i}
                      sx={{ height: SLOT_PX, position: 'relative', pr: 1 }}
                    >
                      {onHour && (
                        <Typography
                          variant="caption"
                          color="text.secondary"
                          sx={{
                            position: 'absolute',
                            top: 0,
                            right: 6,
                            transform: 'translateY(-50%)',
                            whiteSpace: 'nowrap',
                            lineHeight: 1,
                          }}
                        >
                          {String(Math.floor(mins / 60)).padStart(2, '0')}:00
                        </Typography>
                      )}
                    </Box>
                  );
                })}
              </Box>

              {/* Day columns */}
              {days.map((day) => (
                <Box
                  key={day.toISOString()}
                  sx={{
                    flex: 1,
                    position: 'relative',
                    height: colHeight,
                    borderLeft: 1,
                    borderColor: 'divider',
                  }}
                >
                  {Array.from({ length: rows }, (_, i) => (
                    <Box
                      key={i}
                      onMouseDown={(e) => {
                        e.preventDefault();
                        setDrag({ day, startRow: i, endRow: i });
                      }}
                      onMouseEnter={() =>
                        setDrag((d) => (d && isSameDay(d.day, day) ? { ...d, endRow: i } : d))
                      }
                      sx={{
                        height: SLOT_PX,
                        borderTop: 1,
                        borderColor: (i * prefs.slotMinutes) % 60 === 0 ? 'divider' : 'grey.100',
                        cursor: 'pointer',
                        userSelect: 'none',
                        '&:hover': { bgcolor: 'action.hover' },
                      }}
                    />
                  ))}
                  {drag && isSameDay(drag.day, day) && (
                    <Box
                      sx={{
                        position: 'absolute',
                        left: 2,
                        right: 2,
                        top: Math.min(drag.startRow, drag.endRow) * SLOT_PX,
                        height: (Math.abs(drag.endRow - drag.startRow) + 1) * SLOT_PX,
                        bgcolor: 'primary.main',
                        opacity: 0.22,
                        borderRadius: 1,
                        pointerEvents: 'none',
                      }}
                    />
                  )}
                  {eventsForDay(events, day).map((ev) => {
                    const isResizing =
                      resize && eventKey(resize.ev) === eventKey(ev) ? resize : null;
                    return (
                      <EventBlock
                        key={eventKey(ev)}
                        ev={ev}
                        startHour={prefs.startHour}
                        slotMinutes={prefs.slotMinutes}
                        canResize={canWriteContent}
                        preview={isResizing}
                        onResizeStart={(edge, e) => startEventResize(edge, day, ev, e)}
                        onClick={async () => {
                          // A sprint has no /read page — it isn't a record you read, it's a board
                          // you work, so it opens on the Sprints page instead.
                          if (ev.module === 'sprints') {
                            navigate('/sprints');
                            return;
                          }
                          // Opening a virtual occurrence gives it a record — /read needs an id, and
                          // opening one is a deliberate act, not incidental browsing.
                          const id = await resolveId(ev);
                          if (id == null) return;
                          if (ev.virtual) qc.invalidateQueries({ queryKey: ['calendar-events'] });
                          window.open(`/read/${ev.module}/${id}`, '_blank', 'noopener');
                        }}
                        onSkip={
                          canWriteContent && ev.seriesId != null && ev.recurrenceId
                            ? async () => {
                                await skipOccurrence(ev.seriesId!, ev.recurrenceId!);
                                qc.invalidateQueries({ queryKey: ['calendar-events'] });
                              }
                            : undefined
                        }
                      />
                    );
                  })}
                  <NowIndicator
                    day={day}
                    now={now}
                    startHour={prefs.startHour}
                    endHour={prefs.endHour}
                    slotMinutes={prefs.slotMinutes}
                  />
                </Box>
              ))}
            </Box>
          </Box>

          {/* Mini-calendar overview */}
          <Box sx={{ width: 300, flexShrink: 0, borderLeft: 1, borderColor: 'divider', p: 1 }}>
            <DateCalendar
              value={anchor}
              onChange={(d) => d && setAnchor(d as Date)}
              sx={{ width: 290 }}
            />
          </Box>
        </Box>
      </Box>

      {slot && (
        <EventDialog
          open
          start={slot.start}
          end={slot.end}
          onClose={() => setSlot(null)}
          onCreated={() => setSlot(null)}
        />
      )}
    </Box>
  );
}

function eventsForDay(events: CalendarEvent[], day: Date): CalendarEvent[] {
  return events.filter((e) => isSameDay(parseISO(e.start), day));
}

/** Thin red "current time" bar, auto-advancing (see the 30s tick in CalendarPage), on today's
 * column. Outside the visible hours it stays, pinned dashed to the top or bottom edge with the time
 * and an arrow, rather than vanishing — which read as the line being gone. */
function NowIndicator({
  day,
  now,
  startHour,
  endHour,
  slotMinutes,
}: {
  day: Date;
  now: Date;
  startHour: number;
  endHour: number;
  slotMinutes: number;
}) {
  if (!isSameDay(day, now)) return null;
  const minutesFromStart = now.getHours() * 60 + now.getMinutes() - startHour * 60;
  const span = (endHour - startHour) * 60;
  const before = minutesFromStart < 0;
  const after = minutesFromStart > span;
  const top = (Math.min(Math.max(minutesFromStart, 0), span) / slotMinutes) * SLOT_PX;

  return (
    <Box
      sx={{
        position: 'absolute',
        left: 0,
        right: 0,
        top: top - 1,
        ...(before || after
          ? { height: 0, borderTop: '2px dashed', borderColor: 'error.main' }
          : { height: 2, bgcolor: 'error.main' }),
        zIndex: 3,
        pointerEvents: 'none',
      }}
    >
      {(before || after) && (
        <Box
          sx={{
            position: 'absolute',
            right: 4,
            ...(before ? { top: 2 } : { bottom: 2 }),
            px: 0.5,
            fontSize: 11,
            fontWeight: 700,
            lineHeight: 1.4,
            color: 'error.main',
            bgcolor: 'background.paper',
            borderRadius: 0.5,
          }}
        >
          {format(now, 'HH:mm')} {before ? '▲' : '▼'}
        </Box>
      )}
      <Box
        sx={{
          position: 'absolute',
          left: -4,
          top: -3,
          width: 8,
          height: 8,
          borderRadius: '50%',
          bgcolor: 'error.main',
        }}
      />
    </Box>
  );
}

function EventBlock({
  ev,
  startHour,
  slotMinutes,
  canResize,
  preview,
  onResizeStart,
  onClick,
  onSkip,
}: {
  ev: CalendarEvent;
  startHour: number;
  slotMinutes: number;
  canResize: boolean;
  preview: ResizeState | null;
  onResizeStart: (edge: 'start' | 'end', e: React.MouseEvent) => void;
  onClick: () => void;
  /** Present only on an occurrence of a recurring series: drop this one occurrence. */
  onSkip?: () => void;
}) {
  const start = parseISO(ev.start);
  const end = ev.end ? parseISO(ev.end) : new Date(start.getTime() + slotMinutes * 60_000);
  // While this event's edge is being dragged, render the live (snapped) preview span instead of
  // its saved start/end — committed to the server only on mouse-up.
  const startMin = preview ? preview.previewStartMin : start.getHours() * 60 + start.getMinutes();
  const endMin = preview ? preview.previewEndMin : end.getHours() * 60 + end.getMinutes();
  const minutesFromStart = startMin - startHour * 60;
  const durationMin = Math.max(15, endMin - startMin);
  const top = Math.max(0, (minutesFromStart / slotMinutes) * SLOT_PX);
  const height = Math.max(SLOT_PX - 2, (durationMin / slotMinutes) * SLOT_PX - 2);

  return (
    <Tooltip
      title={
        <Box dir="auto">
          <Typography variant="body2" sx={{ fontWeight: 700 }}>
            {ev.title} ({format(start, 'HH:mm')})
          </Typography>
          {ev.seriesId != null && (
            <Typography variant="caption" sx={{ display: 'block' }}>
              {ev.virtual
                ? 'Repeat — opening or moving it makes it a record of its own'
                : 'One occurrence of a repeating plan'}
            </Typography>
          )}
          {ev.description && ev.description !== '?' && (
            <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
              {ev.description}
            </Typography>
          )}
        </Box>
      }
      disableHoverListener={!!preview}
    >
      <Box
        onClick={() => {
          if (preview?.moved) return; // a just-finished drag shouldn't also open the record
          onClick();
        }}
        dir="auto"
        sx={{
          position: 'absolute',
          top,
          left: 2,
          right: 2,
          height,
          ...eventStyle(ev),
          borderRadius: 1,
          px: 0.5,
          py: 0.25,
          fontSize: 12,
          overflow: 'hidden',
          cursor: 'pointer',
          opacity: preview ? 0.85 : 1,
          '&:hover': { filter: 'brightness(1.1)' },
          '&:hover .event-resize-handle': { opacity: 1 },
          '&:hover .event-skip': { opacity: 1 },
        }}
      >
        {onSkip && (
          <Box
            className="event-skip"
            component="span"
            title="Remove just this occurrence"
            onClick={(e) => {
              e.stopPropagation(); // the block itself opens the record
              onSkip();
            }}
            sx={{
              position: 'absolute',
              top: 0,
              right: 2,
              opacity: 0,
              px: 0.5,
              lineHeight: 1.2,
              fontWeight: 700,
              cursor: 'pointer',
              '&:hover': { bgcolor: 'rgba(255,255,255,0.45)', borderRadius: 0.5 },
            }}
          >
            ×
          </Box>
        )}
        {canResize && (
          <>
            <Box
              className="event-resize-handle"
              onMouseDown={(e) => onResizeStart('start', e)}
              sx={{
                position: 'absolute',
                top: 0,
                left: 0,
                right: 0,
                height: 6,
                cursor: 'ns-resize',
                opacity: 0,
                '&:hover': { bgcolor: 'rgba(255,255,255,0.5)' },
              }}
            />
            <Box
              className="event-resize-handle"
              onMouseDown={(e) => onResizeStart('end', e)}
              sx={{
                position: 'absolute',
                bottom: 0,
                left: 0,
                right: 0,
                height: 6,
                cursor: 'ns-resize',
                opacity: 0,
                '&:hover': { bgcolor: 'rgba(255,255,255,0.5)' },
              }}
            />
          </>
        )}
        {ev.seriesId != null && <span title="Repeats">⟳ </span>}
        {`${String(Math.floor(startMin / 60)).padStart(2, '0')}:${String(startMin % 60).padStart(2, '0')}`} {ev.title}
      </Box>
    </Tooltip>
  );
}
