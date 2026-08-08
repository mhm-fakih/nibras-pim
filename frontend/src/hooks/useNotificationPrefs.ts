import { useCallback, useState } from 'react';

/** Per-browser desktop-notification preference (mirrors useTimerPrefs). Notification permission
 * itself is granted per-browser by the OS/browser anyway, so this lives in localStorage rather
 * than the server-side settings store. */
export interface NotificationPrefs {
  enabled: boolean;
}

const KEY = 'nibras.notificationPrefs';
const DEFAULTS: NotificationPrefs = { enabled: true };

function load(): NotificationPrefs {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? { ...DEFAULTS, ...JSON.parse(raw) } : DEFAULTS;
  } catch {
    return DEFAULTS;
  }
}

export function useNotificationPrefs() {
  const [prefs, setPrefs] = useState<NotificationPrefs>(load);
  const update = useCallback((patch: Partial<NotificationPrefs>) => {
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
