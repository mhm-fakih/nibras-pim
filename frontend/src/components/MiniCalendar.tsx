import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import Badge from '@mui/material/Badge';
import { DateCalendar } from '@mui/x-date-pickers/DateCalendar';
import { PickersDay, PickersDayProps } from '@mui/x-date-pickers/PickersDay';
import { getCalendar, getCalendarEvents } from '../api/client';

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

export function MiniCalendar({ entity, selectedDate, onSelect }: Props) {
  const [month, setMonth] = useState<Date>(selectedDate ?? new Date());

  const from = useMemo(() => new Date(month.getFullYear(), month.getMonth(), 1), [month]);
  const to = useMemo(() => new Date(month.getFullYear(), month.getMonth() + 1, 0), [month]);

  const { data } = useQuery({
    queryKey: ['calendar', entity, from.getFullYear(), from.getMonth()],
    queryFn: () => getCalendar(ymd(from), ymd(to), entity),
  });

  const counts = useMemo(() => {
    const m = new Map<string, number>();
    (data ?? []).forEach((d) => m.set(d.date, d.count));
    return m;
  }, [data]);

  // Days that have a Journal entry (by its startDate) — marked with a small green dot.
  const { data: journalEvents } = useQuery({
    queryKey: ['calendar-journals', from.getFullYear(), from.getMonth()],
    queryFn: () => getCalendarEvents(ymd(from), ymd(to), 'journals'),
  });
  const journalDays = useMemo(
    () => new Set((journalEvents ?? []).map((e) => e.start.slice(0, 10))),
    [journalEvents],
  );

  return (
    <DateCalendar
      value={selectedDate}
      onChange={(d) => d && onSelect(d as Date)}
      onMonthChange={(d) => setMonth(d as Date)}
      displayWeekNumber
      reduceAnimations
      slots={{ day: BadgedDay }}
      slotProps={{ day: { counts, journalDays } as never }}
      sx={{
        // Compact, ~half-height mini calendar. en-GB locale (set on the provider) makes weeks start
        // Monday with ISO week numbers; displayWeekNumber adds the leading week-number column.
        width: 252,
        height: 'auto',
        maxHeight: 'unset',
        my: 0,
        '& .MuiPickersCalendarHeader-root': { px: 1, mt: 0, mb: 0, minHeight: 28 },
        '& .MuiPickersCalendarHeader-label': { fontSize: 14 },
        '& .MuiDayCalendar-header': { mb: 0 },
        '& .MuiPickersSlideTransition-root, & .MuiDayCalendar-slideTransition': { minHeight: 156 },
        '& .MuiDayCalendar-weekContainer': { my: 0 },
        // Day + week-number cells: small and tight so the whole grid is roughly half the default height.
        '& .MuiPickersDay-root, & .MuiDayCalendar-weekDayLabel, & .MuiDayCalendar-weekNumber, & .MuiDayCalendar-weekNumberLabel':
          { width: 28, height: 26, margin: 0, fontSize: 12 },
        '& .MuiDayCalendar-weekNumber, & .MuiDayCalendar-weekNumberLabel': { color: 'text.disabled' },
      }}
    />
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
