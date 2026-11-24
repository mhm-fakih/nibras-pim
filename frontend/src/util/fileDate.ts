/**
 * The day a file's name says it is from, as the old scan processor read it: a camera or scanner
 * stamp ("20261114074614", "IMG_20261114_074614", "Screenshot 2026-11-14 at 07.46.14",
 * "audio_2026-11-14_07-46-14") or a bare date ("20261114", "2026-11-14"). Returns the day as
 * yyyy-MM-dd, or null when the name holds no real date.
 */
export function dateFromFileName(name: string): string | null {
  const stamp = /(?<!\d)(\d{4})[-_.]?(\d{2})[-_.]?(\d{2})(?:[ _T.-]?\d{2}[-_.:]?\d{2}(?:[-_.:]?\d{2})?)?(?!\d)/g;
  for (const m of name.matchAll(stamp)) {
    const y = Number(m[1]);
    const mo = Number(m[2]);
    const d = Number(m[3]);
    if (y < 1900 || y > 2100 || mo < 1 || mo > 12 || d < 1 || d > 31) continue;
    if (new Date(y, mo - 1, d).getMonth() !== mo - 1) continue;   // 31 April and the like
    return `${m[1]}-${m[2]}-${m[3]}`;
  }
  return null;
}

/** yyyy-MM-dd of a local date-time string or Date. */
export function isoDay(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}
