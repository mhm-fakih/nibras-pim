import { useCallback, useState } from 'react';

/**
 * Tracks what's already been notified about today so a repeated poll doesn't re-fire the same
 * alert, and the missing-journal reminder fires at most once/day. Resets automatically whenever
 * the stored date no longer matches today (mirrors useTimerPrefs/useCalendarPrefs).
 *
 * `notifiedKeys` is namespaced per module (e.g. "tasks:5", "planners:5") — Task and Planner ids
 * can collide numerically, so a plain id set would wrongly dedup across modules.
 */
export interface NotificationDedup {
  date: string;
  notifiedKeys: string[];
  journalReminderShown: boolean;
}

const KEY = 'nibras.notificationDedup';

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

function empty(): NotificationDedup {
  return { date: today(), notifiedKeys: [], journalReminderShown: false };
}

function load(): NotificationDedup {
  try {
    const raw = localStorage.getItem(KEY);
    const parsed: NotificationDedup | null = raw ? JSON.parse(raw) : null;
    return parsed && parsed.date === today() ? parsed : empty();
  } catch {
    return empty();
  }
}

function persist(v: NotificationDedup) {
  try {
    localStorage.setItem(KEY, JSON.stringify(v));
  } catch {
    /* ignore */
  }
}

export function useNotificationDedup() {
  const [state, setState] = useState<NotificationDedup>(load);

  const markNotified = useCallback((keys: string[]) => {
    setState((s) => {
      const base = s.date === today() ? s : empty();
      const toAdd = keys.filter((k) => !base.notifiedKeys.includes(k));
      if (toAdd.length === 0) return base;
      const next = { ...base, notifiedKeys: [...base.notifiedKeys, ...toAdd] };
      persist(next);
      return next;
    });
  }, []);

  const markJournalReminderShown = useCallback(() => {
    setState((s) => {
      const base = s.date === today() ? s : empty();
      const next = { ...base, journalReminderShown: true };
      persist(next);
      return next;
    });
  }, []);

  return { state: state.date === today() ? state : empty(), markNotified, markJournalReminderShown };
}
