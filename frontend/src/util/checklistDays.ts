/**
 * The dashboard checklist's day columns: the last N days, oldest first, ending today. Weeks run
 * Monday to Sunday, so a column early in the week can fall in LAST week's file — each column says
 * which week holds it.
 */
import type { ChecklistGroup, ChecklistItem, ChecklistMark, ChecklistWeek } from '../api/client';

export const DEFAULT_CHECKLIST_DAYS = 3;
/** At most a week back: the snapshot carries this week and the one before, nothing older. */
export const MAX_CHECKLIST_DAYS = 7;

export interface DayColumn {
  date: Date;
  /** 0 = this week's file, 1 = last week's. */
  weeksBack: 0 | 1;
  /** Monday = 0 … Sunday = 6, the index into an item's marks. */
  dayIndex: number;
  isToday: boolean;
}

const dayIdx = (d: Date) => (d.getDay() + 6) % 7;

export function clampDays(n: unknown): number {
  const v = Math.round(Number(n));
  return Number.isFinite(v) && v >= 1 ? Math.min(v, MAX_CHECKLIST_DAYS) : DEFAULT_CHECKLIST_DAYS;
}

export function dayColumns(today: Date, days: number): DayColumn[] {
  const n = clampDays(days);
  const t = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const monday = new Date(t);
  monday.setDate(t.getDate() - dayIdx(t));
  const out: DayColumn[] = [];
  for (let k = n - 1; k >= 0; k--) {
    const d = new Date(t);
    d.setDate(t.getDate() - k);
    out.push({ date: d, weeksBack: d < monday ? 1 : 0, dayIndex: dayIdx(d), isToday: k === 0 });
  }
  return out;
}

/**
 * The mark an item had on a column's day. Items are matched across weeks by group and item code,
 * falling back to the item's text — a week file rewritten on the phone can renumber. A weekly
 * group has one mark per week, shown on every day of that week. Unknown → 'NONE'.
 */
export function markOn(
  group: ChecklistGroup,
  item: ChecklistItem,
  col: DayColumn,
  week: ChecklistWeek | null,
  previous: ChecklistWeek | null,
): ChecklistMark {
  const src = col.weeksBack === 0 ? week : previous;
  if (!src) return 'NONE';
  const g = src.groups.find((x) => x.code === group.code) ?? src.groups.find((x) => x.title === group.title);
  const it = g?.items.find((x) => x.code === item.code && x.text === item.text) ?? g?.items.find((x) => x.text === item.text);
  if (!g || !it) return 'NONE';
  return (g.weekly ? it.marks[0] : it.marks[col.dayIndex]) ?? 'NONE';
}
