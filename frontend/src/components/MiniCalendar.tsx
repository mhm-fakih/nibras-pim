import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import Badge from '@mui/material/Badge';
import { Box, IconButton, Stack, Tooltip, Typography } from '@mui/material';
import ExpandMore from '@mui/icons-material/ExpandMore';
import ExpandLess from '@mui/icons-material/ExpandLess';
import { DateCalendar } from '@mui/x-date-pickers/DateCalendar';
import { PickersDay, PickersDayProps } from '@mui/x-date-pickers/PickersDay';
import { getCalendar, getCalendarEvents } from '../api/client';
import { useModules } from '../hooks/useModules';

interface Props {
  entity: string;
  selectedDate: Date | null;
  onSelect: (d: Date) => void;
}

function ymd(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
    d.getDate(),
  ).padStart(2, '0')}`;
}

/** Monday of the week containing `d` (en-GB/ISO weeks, matching the full calendar below). */
function mondayOf(d: Date): Date {
  const out = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  out.setDate(out.getDate() - ((out.getDay() + 6) % 7));
  return out;
}

const EXPANDED_KEY = 'nibras.miniCalendar.expanded';

/**
 * The rail's date picker. Collapsed (the default) it shows just the previous and current week —
 * two rows, which is all that's needed to jump to "yesterday" or "last Tuesday" and leaves the
 * reports/saved-searches list below it room to breathe. Expanding swaps in the full month grid.
 */
export function MiniCalendar({ entity, selectedDate, onSelect }: Props) {
  const [expanded, setExpanded] = useState(() => localStorage.getItem(EXPANDED_KEY) === '1');
  const [month, setMonth] = useState<Date>(selectedDate ?? new Date());

  const today = useMemo(() => new Date(), []);
  // Two-week window: Monday of last week → Sunday of this week.
  const twoWeekStart = useMemo(() => {
    const m = mondayOf(today);
    m.setDate(m.getDate() - 7);
    return m;
  }, [today]);
  const twoWeekDays = useMemo(
    () =>
      Array.from({ length: 14 }, (_, i) => {
        const d = new Date(twoWeekStart);
        d.setDate(d.getDate() + i);
        return d;
      }),
    [twoWeekStart],
  );

  // Fetch exactly the visible range, so the collapsed view doesn't pull a whole month it can't show.
  const from = expanded ? new Date(month.getFullYear(), month.getMonth(), 1) : twoWeekDays[0];
  const to = expanded ? new Date(month.getFullYear(), month.getMonth() + 1, 0) : twoWeekDays[13];

  const { data } = useQuery({
    queryKey: ['calendar', entity, ymd(from), ymd(to)],
    queryFn: () => getCalendar(ymd(from), ymd(to), entity),
  });

  const counts = useMemo(() => {
    const m = new Map<string, number>();
    (data ?? []).forEach((d) => m.set(d.date, d.count));
    return m;
  }, [data]);

  // Days that have a Journal entry (by its startDate) — marked with a bolder day number. Skipped
  // entirely when journals are switched off: the mark would be a promise the click no longer keeps
  // (see AppShell.handleDateSelect — with journals off, a day opens what the enabled modules hold
  // that day and creates nothing), and there is no reason to query a module that isn't in use.
  const { enabled: enabledModules } = useModules();
  const journalsEnabled = enabledModules.some((m) => m.moduleKey === 'journals');
  const { data: journalEvents } = useQuery({
    queryKey: ['calendar-journals', ymd(from), ymd(to)],
    queryFn: () => getCalendarEvents(ymd(from), ymd(to), 'journals'),
    enabled: journalsEnabled,
  });
  const journalDays = useMemo(
    () => (journalsEnabled ? new Set((journalEvents ?? []).map((e) => e.start.slice(0, 10))) : new Set<string>()),
    [journalEvents, journalsEnabled],
  );

  const toggle = () => {
    setExpanded((e) => {
      localStorage.setItem(EXPANDED_KEY, e ? '0' : '1');
      return !e;
    });
  };

  const header = (
    <Stack direction="row" alignItems="center" sx={{ px: 1.5, pt: 0.5 }}>
      <Typography variant="caption" color="text.secondary" sx={{ flex: 1 }}>
        {expanded
          ? month.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })
          : 'Last 2 weeks'}
      </Typography>
      <Tooltip title={expanded ? 'Show two weeks' : 'Show the full month'}>
        <IconButton size="small" onClick={toggle}>
          {expanded ? <ExpandLess fontSize="small" /> : <ExpandMore fontSize="small" />}
        </IconButton>
      </Tooltip>
    </Stack>
  );

  if (expanded) {
    return (
      <Box>
        {header}
        <DateCalendar
          value={selectedDate}
          onChange={(d) => d && onSelect(d as Date)}
          onMonthChange={(d) => setMonth(d as Date)}
          displayWeekNumber
          reduceAnimations
          slots={{ day: BadgedDay }}
          slotProps={{ day: { counts, journalDays } as never }}
          sx={{
            // Compact, ~half-height mini calendar. en-GB locale (set on the provider) makes weeks
            // start Monday with ISO week numbers; displayWeekNumber adds the week-number column.
            width: 252,
            height: 'auto',
            maxHeight: 'unset',
            my: 0,
            '& .MuiPickersCalendarHeader-root': { px: 1, mt: 0, mb: 0, minHeight: 28 },
            '& .MuiPickersCalendarHeader-label': { fontSize: 14 },
            '& .MuiDayCalendar-header': { mb: 0 },
            '& .MuiPickersSlideTransition-root, & .MuiDayCalendar-slideTransition': { minHeight: 156 },
            '& .MuiDayCalendar-weekContainer': { my: 0 },
            '& .MuiPickersDay-root, & .MuiDayCalendar-weekDayLabel, & .MuiDayCalendar-weekNumber, & .MuiDayCalendar-weekNumberLabel':
              { width: 28, height: 26, margin: 0, fontSize: 12 },
            '& .MuiDayCalendar-weekNumber, & .MuiDayCalendar-weekNumberLabel': { color: 'text.disabled' },
          }}
        />
      </Box>
    );
  }

  const selectedKey = selectedDate ? ymd(selectedDate) : null;
  const todayKey = ymd(today);

  return (
    <Box>
      {header}
      <Box sx={{ px: 1.5, pb: 0.5 }}>
        <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 0.25 }}>
          {['M', 'T', 'W', 'T', 'F', 'S', 'S'].map((d, i) => (
            <Typography
              key={i}
              variant="caption"
              align="center"
              sx={{ color: 'text.disabled', fontSize: 10 }}
            >
              {d}
            </Typography>
          ))}
          {twoWeekDays.map((d) => {
            const key = ymd(d);
            const isSelected = key === selectedKey;
            const isToday = key === todayKey;
            const hasRecords = (counts.get(key) ?? 0) > 0;
            return (
              <Box
                key={key}
                onClick={() => onSelect(d)}
                sx={{
                  position: 'relative',
                  textAlign: 'center',
                  fontSize: 12,
                  lineHeight: '26px',
                  height: 26,
                  borderRadius: '50%',
                  cursor: 'pointer',
                  fontWeight: journalDays.has(key) ? 700 : 400,
                  bgcolor: isSelected ? 'primary.main' : undefined,
                  color: isSelected ? 'primary.contrastText' : undefined,
                  border: isToday && !isSelected ? '1px solid' : '1px solid transparent',
                  borderColor: isToday && !isSelected ? 'primary.main' : 'transparent',
                  '&:hover': { bgcolor: isSelected ? 'primary.dark' : 'action.hover' },
                }}
              >
                {d.getDate()}
                {hasRecords && (
                  <Box
                    sx={{
                      position: 'absolute',
                      bottom: 2,
                      left: '50%',
                      transform: 'translateX(-50%)',
                      width: 4,
                      height: 4,
                      borderRadius: '50%',
                      bgcolor: isSelected ? 'primary.contrastText' : 'primary.main',
                      opacity: 0.55,
                    }}
                  />
                )}
              </Box>
            );
          })}
        </Box>
      </Box>
    </Box>
  );
}

function BadgedDay(
  props: PickersDayProps<Date> & { counts?: Map<string, number>; journalDays?: Set<string> },
) {
  const { counts, journalDays, day, outsideCurrentMonth, ...other } = props;
  const key = ymd(day);
  const hasEntity = !outsideCurrentMonth && (counts?.get(key) ?? 0) > 0;
  const hasJournal = !outsideCurrentMonth && (journalDays?.has(key) ?? false);
  return (
    // Bold day number: a Journal entry exists for this day (prettier than a dot). Blue dot
    // (bottom-right): the active module has records created that day.
    <Badge
      overlap="circular"
      variant="dot"
      color="primary"
      invisible={!hasEntity}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      sx={{
        '& .MuiBadge-badge': {
          minWidth: 4,
          height: 4,
          borderRadius: '50%',
          opacity: 0.55,
          transform: 'scale(1) translate(-2px, -2px)',
        },
      }}
    >
      <PickersDay
        {...other}
        day={day}
        outsideCurrentMonth={outsideCurrentMonth}
        sx={hasJournal ? { fontWeight: 700 } : undefined}
      />
    </Badge>
  );
}
