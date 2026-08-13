import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getSetting, listEntities } from '../api/client';
import { useNotificationPrefs } from '../hooks/useNotificationPrefs';
import { useNotificationDedup } from '../hooks/useNotificationDedup';
import { useRunningTimer } from '../hooks/useRunningTimer';

const POLL_MS = 5 * 60 * 1000; // 5 minutes — due-date checks
/** How often to nudge when no time-tracking session is running. */
const IDLE_TIMER_NUDGE_MS = 15 * 60 * 1000;
const JOURNAL_POLL_MS = 60 * 60 * 1000; // hourly is plenty for an hour-granularity threshold

function notify(title: string, body: string, tag: string) {
  if (typeof Notification === 'undefined' || Notification.permission !== 'granted') return;
  const n = new Notification(title, { body, tag });
  n.onclick = () => {
    window.focus();
    n.close();
  };
}

/**
 * Always mounted (inside ShortcutsProvider, alongside the routed pages — see main.tsx) so it
 * polls and can fire a desktop notification regardless of which page is open. Reuses the
 * backend's own overdue/dueSoon report-preset definitions (AbstractCrudService.spec()) rather
 * than reimplementing due-date math here, so the threshold always matches what the Reports rail
 * shows. Renders nothing.
 */
export function NotificationWatcher() {
  const { prefs } = useNotificationPrefs();
  const { state, markNotified, markJournalReminderShown } = useNotificationDedup();
  const supported = typeof Notification !== 'undefined';
  const active = supported && prefs.enabled && Notification.permission === 'granted';

  // "Nothing is being tracked" — every 15 minutes, and only while nothing is.
  //
  // The interval is deliberately re-armed from scratch whenever a timer starts or stops rather than
  // running always and checking: an interval that keeps ticking through a tracked session would
  // fire the moment you stopped, which reads as nagging rather than reminding. Tied to the shared
  // store, so a timer started anywhere — a record page, a sprint row, another tab — silences it.
  const running = useRunningTimer();
  useEffect(() => {
    if (!active || running) return;
    const id = window.setInterval(() => {
      notify(
        'No timer running',
        'Nothing is being tracked at the moment. Start one on a task or from the sprint board.',
        'nibras-timer-idle',
      );
    }, IDLE_TIMER_NUDGE_MS);
    return () => window.clearInterval(id);
  }, [active, running]);

  const overdueUrgentTasks = useQuery({
    queryKey: ['notify', 'tasks-overdue-urgent'],
    queryFn: () => listEntities('tasks', { overdue: true, urgent: true, size: 50 }),
    enabled: active,
    refetchInterval: active ? POLL_MS : false,
  });
  const overdueTasks = useQuery({
    queryKey: ['notify', 'tasks-overdue'],
    queryFn: () => listEntities('tasks', { overdue: true, size: 50 }),
    enabled: active,
    refetchInterval: active ? POLL_MS : false,
  });
  const dueSoonTasks = useQuery({
    queryKey: ['notify', 'tasks-due-soon'],
    queryFn: () => listEntities('tasks', { dueSoon: true, size: 50 }),
    enabled: active,
    refetchInterval: active ? POLL_MS : false,
  });
  const overduePlanners = useQuery({
    queryKey: ['notify', 'planners-overdue'],
    queryFn: () => listEntities('planners', { overdue: true, size: 50 }),
    enabled: active,
    refetchInterval: active ? POLL_MS : false,
  });
  const dueSoonPlanners = useQuery({
    queryKey: ['notify', 'planners-due-soon'],
    queryFn: () => listEntities('planners', { dueSoon: true, size: 50 }),
    enabled: active,
    refetchInterval: active ? POLL_MS : false,
  });

  useEffect(() => {
    if (!active) return;
    if (!overdueTasks.data || !dueSoonTasks.data || !overduePlanners.data || !dueSoonPlanners.data) return;

    const overdue = [
      ...overdueTasks.data.content.map((r) => ({ key: `tasks:${r.id}` })),
      ...overduePlanners.data.content.map((r) => ({ key: `planners:${r.id}` })),
    ];
    const dueSoon = [
      ...dueSoonTasks.data.content.map((r) => ({ key: `tasks:${r.id}` })),
      ...dueSoonPlanners.data.content.map((r) => ({ key: `planners:${r.id}` })),
    ];
    const allKeys = [...overdue, ...dueSoon].map((x) => x.key);
    const fresh = allKeys.filter((k) => !state.notifiedKeys.includes(k));
    if (fresh.length === 0) return;

    const urgentCount = overdueUrgentTasks.data?.content.length ?? 0;
    if (overdue.length > 0) {
      const urgentNote = urgentCount > 0 ? ` (${urgentCount} urgent)` : '';
      notify(
        `Nibras — ${overdue.length} overdue${urgentNote}`,
        'Open Nibras to review overdue tasks and plans.',
        'nibras-overdue',
      );
    } else if (dueSoon.length > 0) {
      notify(
        `Nibras — ${dueSoon.length} due within a week`,
        'Open Nibras to review upcoming items.',
        'nibras-due-soon',
      );
    }
    markNotified(allKeys);
    // Deliberately re-runs only when the underlying query data changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    active,
    overdueTasks.data,
    dueSoonTasks.data,
    overduePlanners.data,
    dueSoonPlanners.data,
    overdueUrgentTasks.data,
  ]);

  // Missing-journal-entry reminder, checked hourly, fires at most once/day past the configured hour.
  const reminderHour = useQuery({
    queryKey: ['notify', 'journal-reminder-hour'],
    queryFn: () => getSetting('journal.reminder-hour', '17'),
    enabled: active,
    refetchInterval: active ? JOURNAL_POLL_MS : false,
  });
  const todayIso = new Date().toISOString().slice(0, 10);
  const todaysJournal = useQuery({
    queryKey: ['notify', 'journal-today', todayIso],
    queryFn: () => listEntities('journals', { onDay: todayIso, size: 1 }),
    enabled: active && reminderHour.data != null,
    refetchInterval: active ? JOURNAL_POLL_MS : false,
  });

  useEffect(() => {
    if (!active || state.journalReminderShown) return;
    const hour = Number(reminderHour.data ?? '17');
    if (Number.isNaN(hour) || new Date().getHours() < hour) return;
    if (todaysJournal.data == null || todaysJournal.data.totalElements > 0) return;
    notify(
      'Nibras — no journal entry today',
      "You haven't logged a journal entry yet today.",
      'nibras-journal-reminder',
    );
    markJournalReminderShown();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active, reminderHour.data, todaysJournal.data, state.journalReminderShown]);

  return null;
}
