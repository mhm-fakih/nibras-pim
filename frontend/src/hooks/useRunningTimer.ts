import { useSyncExternalStore } from 'react';
import {
  createEntity,
  getSetting,
  linkRecord,
  reportTimerStart,
  reportTimerStop,
  EntityRecord,
} from '../api/client';

/** The one timer that may be running, and when it started. */
export interface RunningTimer {
  taskId: number;
  startMs: number;
}

/**
 * ONE key for the whole app, not one per task — that is the mutual exclusion.
 *
 * The tracker used to persist under `nibras.timer.task.<id>`, so every task had its own
 * independent slot and nothing stopped three of them running at once. Two trackers for the same
 * task (its record page and its row in a sprint) couldn't even see each other. A single key makes
 * "which timer is running" a fact about the app rather than about whichever component happens to
 * be mounted.
 */
const KEY = 'nibras.timer.running';
/** The per-task keys this replaced, still read once so a session in flight isn't stranded. */
const LEGACY_PREFIX = 'nibras.timer.task.';

const listeners = new Set<() => void>();

/** Setting key for the auto-stop, in minutes. */
export const MAX_SLOT_SETTING = 'timer.max-slot-minutes';
/** Used when the setting is unset or unusable. */
export const DEFAULT_MAX_SLOT_MINUTES = 60;

/** Handle for the pending auto-stop, so starting or stopping cancels the previous one. */
let autoStop: number | null = null;

/**
 * Desktop notification, if the browser has been granted permission. Duplicated rather than imported
 * from NotificationWatcher because this is a store, not a component — importing a component module
 * here would drag React rendering into a plain module for three lines.
 */
export function notifyDesktop(title: string, body: string, tag: string) {
  try {
    if (typeof Notification === 'undefined' || Notification.permission !== 'granted') return;
    const n = new Notification(title, { body, tag });
    n.onclick = () => {
      window.focus();
      n.close();
    };
  } catch {
    /* notifications are a courtesy; never let one break a save */
  }
}

/** The configured maximum session length, in minutes. Never zero or negative. */
export async function maxSlotMinutes(): Promise<number> {
  try {
    const raw = await getSetting(MAX_SLOT_SETTING, String(DEFAULT_MAX_SLOT_MINUTES));
    const n = Number(raw);
    return Number.isFinite(n) && n > 0 ? n : DEFAULT_MAX_SLOT_MINUTES;
  } catch {
    return DEFAULT_MAX_SLOT_MINUTES;
  }
}

/**
 * Arm the auto-stop for whatever is running.
 *
 * This lives in the store, not in the tracker component, because the component is not reliably
 * there. A timer started on a sprint row and then navigated away from had nothing left to fire its
 * cut-off — the session simply ran until someone came back to that exact row. Here it is armed once
 * per running session, survives moving between pages, and is re-armed on load from the persisted
 * start time.
 *
 * A deadline already in the past (the tab was closed overnight) stops immediately and records the
 * session ending at start + max rather than now: the point of a maximum is that no session exceeds
 * it, and "I forgot to press stop" should not become a twelve-hour journal entry.
 */
async function armAutoStop() {
  if (autoStop !== null) {
    window.clearTimeout(autoStop);
    autoStop = null;
  }
  const current = snapshot;
  if (!current) {
    return;
  }
  const limitMs = (await maxSlotMinutes()) * 60_000;
  const deadline = current.startMs + limitMs;
  const remaining = deadline - Date.now();
  const announce = (minutes: number) =>
    notifyDesktop(
      'Timer stopped',
      `The session hit its ${minutes}-minute limit and was saved. Start another when you pick the work back up.`,
      'nibras-timer-timeout',
    );
  if (remaining <= 0) {
    await stopTimer('', deadline);
    announce(limitMs / 60_000);
    return;
  }
  autoStop = window.setTimeout(() => {
    void stopTimer('', current.startMs + limitMs).then(() => announce(limitMs / 60_000));
  }, remaining);
}

function notify() {
  listeners.forEach((l) => l());
}

/** Cached so useSyncExternalStore's getSnapshot can return a stable reference between changes. */
let snapshot: RunningTimer | null = readStorage();

function readStorage(): RunningTimer | null {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as RunningTimer;
      return typeof parsed?.taskId === 'number' && typeof parsed?.startMs === 'number' ? parsed : null;
    }
    // One-time migration: adopt a session that was running under the old per-task scheme, so an
    // upgrade mid-session doesn't silently lose the elapsed time.
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i);
      if (!k?.startsWith(LEGACY_PREFIX)) continue;
      const startMs = Number(localStorage.getItem(k));
      const taskId = Number(k.slice(LEGACY_PREFIX.length));
      localStorage.removeItem(k);
      if (Number.isFinite(startMs) && Number.isFinite(taskId)) {
        const adopted = { taskId, startMs };
        localStorage.setItem(KEY, JSON.stringify(adopted));
        return adopted;
      }
    }
  } catch {
    /* a corrupt or unavailable localStorage must not break the page */
  }
  return null;
}

function write(next: RunningTimer | null) {
  try {
    if (next) localStorage.setItem(KEY, JSON.stringify(next));
    else localStorage.removeItem(KEY);
  } catch {
    /* ignore */
  }
  snapshot = next;
  notify();
}

/** Local-time ISO with no zone — the shape the backend's LocalDateTime expects. */
function toLocalIso(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(
    d.getMinutes(),
  )}:${p(d.getSeconds())}`;
}

/**
 * Write one tracked session: a Journal spanning start→end, linked to the task. Exported as a plain
 * function rather than living in the component, because stopping a timer often happens from a
 * DIFFERENT task's row — the one whose component is mounted is not the one being stopped.
 */
export async function saveSession(
  taskId: number,
  start: Date,
  end: Date,
  note: string,
): Promise<EntityRecord> {
  const journal = (await createEntity('journals', {
    startDate: toLocalIso(start),
    endDate: toLocalIso(end),
    summary: note.trim() || null,
  })) as EntityRecord;
  await linkRecord('journals', journal.id, 'task', taskId);
  return journal;
}

/** Subscribe to "which timer is running" — same answer in every component, updated live. */
export function useRunningTimer(): RunningTimer | null {
  return useSyncExternalStore(
    (onChange) => {
      listeners.add(onChange);
      // Another tab starting a timer counts too: the exclusion is per user, not per tab.
      const onStorage = (e: StorageEvent) => {
        if (e.key === KEY) {
          snapshot = readStorage();
          onChange();
        }
      };
      window.addEventListener('storage', onStorage);
      return () => {
        listeners.delete(onChange);
        window.removeEventListener('storage', onStorage);
      };
    },
    () => snapshot,
    () => null,
  );
}

/**
 * Start tracking `taskId`, stopping and SAVING whatever was running first.
 *
 * Saving rather than discarding is the point: the previous session was real work, and the
 * natural way to move between tasks is to start the next one. Losing the first would make that
 * gesture destructive.
 */
export async function startTimer(taskId: number): Promise<void> {
  const current = snapshot;
  if (current && current.taskId === taskId) {
    return;   // already running for this task; starting again is a no-op, not a restart
  }
  if (current) {
    await stopTimer('');
  }
  const now = Date.now();
  write({ taskId, startMs: now });
  reportTimerStart(taskId);   // server-side flag for the status bar / external probes
  void armAutoStop();
}

/**
 * Stop the running timer and save its session. No-op when nothing is running.
 *
 * `endMs` exists for the auto-stop, which ends the session at the cut-off rather than at the moment
 * it happened to notice — those differ whenever the tab was asleep or closed.
 */
export async function stopTimer(note: string, endMs?: number): Promise<EntityRecord | null> {
  const current = snapshot;
  if (!current) {
    return null;
  }
  if (autoStop !== null) {
    window.clearTimeout(autoStop);
    autoStop = null;
  }
  write(null);
  reportTimerStop();
  const end = new Date(endMs ?? Date.now());
  return saveSession(current.taskId, new Date(current.startMs), end, note);
}

// A session restored from localStorage (page reload, or the tab reopened) needs its cut-off armed
// again — the setTimeout that would have fired it died with the previous page.
if (snapshot) {
  void armAutoStop();
}
