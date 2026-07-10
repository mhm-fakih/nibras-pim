import { useCallback, useState } from 'react';

/** Per-browser time-tracking preferences (mirrors useCalendarPrefs). */
export interface TimerPrefs {
  /** Auto-stop a running session after this many minutes (default 1h). */
  maxSlotMinutes: number;
}

const KEY = 'nibras.timerPrefs';
const DEFAULTS: TimerPrefs = { maxSlotMinutes: 60 };

function load(): TimerPrefs {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? { ...DEFAULTS, ...JSON.parse(raw) } : DEFAULTS;
  } catch {
    return DEFAULTS;
  }
}

export function useTimerPrefs() {
  const [prefs, setPrefs] = useState<TimerPrefs>(load);
  const update = useCallback((patch: Partial<TimerPrefs>) => {
    setPrefs((p) => {
      const next = { ...p, ...patch };
      try {
        localStorage.setItem(KEY, JSON.stringify(next));
      } catch {
        /* ignore */
      }
      return next;
    });
  }, []);
  return { prefs, update };
}
