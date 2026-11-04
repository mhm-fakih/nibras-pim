/**
 * 12- or 24-hour times for the calendars (Settings → Calendar → "24-hour clock"; 12-hour by
 * default). Pure functions — the setting itself is read by `hooks/useHour12`.
 */

/** "1 PM" / "13:00" — the label on an hour line. */
export function fmtHour(h: number, hour12: boolean): string {
  const hh = ((h % 24) + 24) % 24;
  if (!hour12) return `${String(hh).padStart(2, '0')}:00`;
  return `${hh % 12 === 0 ? 12 : hh % 12} ${hh < 12 ? 'AM' : 'PM'}`;
}

/** "1:05 PM" / "13:05" from minutes after midnight. */
export function fmtMinutes(mins: number, hour12: boolean): string {
  const m = ((Math.floor(mins) % 1440) + 1440) % 1440;
  const h = Math.floor(m / 60);
  const mm = String(m % 60).padStart(2, '0');
  if (!hour12) return `${String(h).padStart(2, '0')}:${mm}`;
  return `${h % 12 === 0 ? 12 : h % 12}:${mm} ${h < 12 ? 'AM' : 'PM'}`;
}

/** "1:05 PM" / "13:05" for a Date's local time. */
export function fmtTime(d: Date, hour12: boolean): string {
  return fmtMinutes(d.getHours() * 60 + d.getMinutes(), hour12);
}

/** The stored setting ('true'/'false'); anything else (unset) means 12-hour. */
export function parseHour12(value: string | null | undefined): boolean {
  return value !== 'false';
}
