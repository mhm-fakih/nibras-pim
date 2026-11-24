/**
 * ISO weeks as the checklists name them: "2026-W46", Monday to Sunday, week 1 holding the year's
 * first Thursday. Dates are plain "yyyy-mm-dd" strings handled in UTC, so no time zone can move a
 * day across midnight.
 */

function utc(iso: string): Date {
  return new Date(`${iso}T00:00:00Z`);
}

function isoOf(d: Date): string {
  return d.toISOString().slice(0, 10);
}

/** "2026-11-09" plus n days. */
export function addDaysIso(iso: string, n: number): string {
  const d = utc(iso);
  d.setUTCDate(d.getUTCDate() + n);
  return isoOf(d);
}

/** The week a (local) date falls in. */
export function weekIdOf(date: Date): string {
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  const day = (d.getUTCDay() + 6) % 7; // Monday = 0
  d.setUTCDate(d.getUTCDate() - day + 3); // the week's Thursday decides its year
  const year = d.getUTCFullYear();
  const firstThursday = new Date(Date.UTC(year, 0, 4));
  firstThursday.setUTCDate(firstThursday.getUTCDate() - ((firstThursday.getUTCDay() + 6) % 7) + 3);
  const week = 1 + Math.round((d.getTime() - firstThursday.getTime()) / (7 * 86_400_000));
  return `${year}-W${String(week).padStart(2, '0')}`;
}

/** The Monday of week "2026-W46", as "yyyy-mm-dd". */
export function mondayOfWeekId(id: string): string {
  const [y, w] = id.split('-W').map(Number);
  const jan4 = new Date(Date.UTC(y, 0, 4));
  const monday1 = new Date(jan4);
  monday1.setUTCDate(jan4.getUTCDate() - ((jan4.getUTCDay() + 6) % 7));
  monday1.setUTCDate(monday1.getUTCDate() + (w - 1) * 7);
  return isoOf(monday1);
}

/** The week n weeks after (or before, for negative n) week `id`. */
export function shiftWeek(id: string, n: number): string {
  const monday = utc(addDaysIso(mondayOfWeekId(id), n * 7));
  return weekIdOf(new Date(monday.getUTCFullYear(), monday.getUTCMonth(), monday.getUTCDate()));
}
