import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  addDays,
  addWeeks,
  differenceInMinutes,
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
import { CalendarEvent, getCalendarEvents } from '../api/client';
import { SideNav } from '../components/SideNav';
import { EventDialog } from '../components/EventDialog';
import { useAuth } from '../hooks/useAuth';
import { useCalendarPrefs } from '../hooks/useCalendarPrefs';

const SLOT_PX = 26;
const GUTTER = 56;
const MODULE_COLOR: Record<string, string> = { planners: '#0078d4', journals: '#8764b8' };

function ymd(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
    d.getDate(),
  ).padStart(2, '0')}`;
}

export function CalendarPage() {
  const navigate = useNavigate();
  const { canWriteContent } = useAuth();
  const { prefs, update } = useCalendarPrefs();
  const [anchor, setAnchor] = useState<Date>(new Date());
  const [slot, setSlot] = useState<{ start: Date; end: Date } | null>(null);
  // Click-drag selection across adjacent slots in one day column.
  const [drag, setDrag] = useState<{ day: Date; startRow: number; endRow: number } | null>(null);

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
                    bgcolor: isSameDay(d, new Date()) ? 'action.hover' : undefined,
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
                  {eventsForDay(events, day).map((ev) => (
                    <EventBlock
                      key={`${ev.module}-${ev.id}`}
                      ev={ev}
                      startHour={prefs.startHour}
                      slotMinutes={prefs.slotMinutes}
                      onClick={() => navigate(`/${ev.module}`)}
                    />
                  ))}
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

function EventBlock({
  ev,
  startHour,
  slotMinutes,
  onClick,
}: {
  ev: CalendarEvent;
  startHour: number;
  slotMinutes: number;
  onClick: () => void;
}) {
  const start = parseISO(ev.start);
  const end = ev.end ? parseISO(ev.end) : new Date(start.getTime() + slotMinutes * 60_000);
  const minutesFromStart = start.getHours() * 60 + start.getMinutes() - startHour * 60;
  const durationMin = Math.max(15, differenceInMinutes(end, start));
  const top = Math.max(0, (minutesFromStart / slotMinutes) * SLOT_PX);
  const height = Math.max(SLOT_PX - 2, (durationMin / slotMinutes) * SLOT_PX - 2);

  return (
    <Tooltip title={`${ev.title} (${format(start, 'HH:mm')})`}>
      <Box
        onClick={onClick}
        dir="auto"
        sx={{
          position: 'absolute',
          top,
          left: 2,
          right: 2,
          height,
          bgcolor: MODULE_COLOR[ev.module] ?? 'primary.main',
          color: '#fff',
          borderRadius: 1,
          px: 0.5,
          py: 0.25,
          fontSize: 12,
          overflow: 'hidden',
          cursor: 'pointer',
          '&:hover': { filter: 'brightness(1.1)' },
        }}
      >
        {format(start, 'HH:mm')} {ev.title}
      </Box>
    </Tooltip>
  );
}
