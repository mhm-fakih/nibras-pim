import { useCallback, useState } from 'react';

export type SlotMinutes = 15 | 30 | 60;
export type CalendarView = 'week' | 'day';

export interface CalendarPrefs {
  slotMinutes: SlotMinutes;
  startHour: number; // 0–23
  endHour: number; // 1–24
  view: CalendarView;
}

const KEY = 'nibras.calendarPrefs';
const DEFAULTS: CalendarPrefs = { slotMinutes: 30, startHour: 7, endHour: 21, view: 'week' };

function load(): CalendarPrefs {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? { ...DEFAULTS, ...JSON.parse(raw) } : DEFAULTS;
  } catch {
    return DEFAULTS;
  }
}

/** Calendar view preferences, persisted in localStorage (per browser). */
export function useCalendarPrefs() {
  const [prefs, setPrefs] = useState<CalendarPrefs>(load);

  const update = useCallback((patch: Partial<CalendarPrefs>) => {
    setPrefs((p) => {
      const next = { ...p, ...patch };
      try {
        localStorage.setItem(KEY, JSON.stringify(next));
      } catch {
        /* ignore quota errors */
      }
      return next;
    });
  }, []);

  return { prefs, update };
}
