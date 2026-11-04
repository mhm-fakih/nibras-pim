import { fmtHour, fmtMinutes, fmtTime, parseHour12 } from '../src/util/clock';
import { clampDays, dayColumns, markOn } from '../src/util/checklistDays';

/** The calendars' 12/24-hour labels, and the dashboard checklist's day columns. */
export default function run() {
  let pass = 0;
  let fail = 0;
  const check = (name, got, want) => {
    const ok = JSON.stringify(got) === JSON.stringify(want);
    if (ok) {
      pass++;
      console.log('  ok  ', name);
    } else {
      fail++;
      console.log('  FAIL', name, '\n        got: ', JSON.stringify(got), '\n        want:', JSON.stringify(want));
    }
  };

  check('12h hours', [0, 1, 11, 12, 13, 23].map((h) => fmtHour(h, true)), ['12 AM', '1 AM', '11 AM', '12 PM', '1 PM', '11 PM']);
  check('24h hours', [0, 9, 13].map((h) => fmtHour(h, false)), ['00:00', '09:00', '13:00']);
  check('12h minutes', [0, 65, 725, 1439].map((m) => fmtMinutes(m, true)), ['12:00 AM', '1:05 AM', '12:05 PM', '11:59 PM']);
  check('24h minutes', [65, 1439].map((m) => fmtMinutes(m, false)), ['01:05', '23:59']);
  check('date time', fmtTime(new Date(2026, 10, 3, 18, 7), true), '6:07 PM');
  check('unset setting means 12h', [parseHour12(undefined), parseHour12(''), parseHour12('true'), parseHour12('false')], [true, true, true, false]);

  // Tuesday 2026-11-03: three days back reach Sunday 2026-11-01, in last week's file.
  const tue = new Date(2026, 10, 3, 15, 0);
  const cols = dayColumns(tue, 3);
  check('3 columns, oldest first', cols.map((c) => [c.date.getDate(), c.weeksBack, c.dayIndex, c.isToday]), [
    [1, 1, 6, false],
    [2, 0, 0, false],
    [3, 0, 1, true],
  ]);
  check('days clamp', [clampDays(0), clampDays(9), clampDays('5'), clampDays(undefined)], [3, 7, 5, 3]);
  check('a week back from Sunday stays in this week', dayColumns(new Date(2026, 10, 8), 7).every((c) => c.weeksBack === 0), true);

  const mk = (code, marks, weekly = false, text = 'Read') => ({
    groups: [{ code: 1, title: 'Daily', weekly, items: [{ code, text, marks }] }],
  });
  const week = mk(5, ['DONE', 'PARTIAL', 'NONE', 'NONE', 'NONE', 'NONE', 'NONE']);
  const previous = mk(9, ['NONE', 'NONE', 'NONE', 'NONE', 'NONE', 'NONE', 'DONE']); // renumbered item
  const g = week.groups[0];
  const it = g.items[0];
  check('marks across the week boundary, matched by text', cols.map((c) => markOn(g, it, c, week, previous)), ['DONE', 'DONE', 'PARTIAL']);
  check('no previous week → NONE', markOn(g, it, cols[0], week, null), 'NONE');
  const weekly = mk(5, ['DONE'], true);
  check('weekly group: the week mark on each day', cols.slice(1).map((c) => markOn(weekly.groups[0], weekly.groups[0].items[0], c, weekly, null)), ['DONE', 'DONE']);

  console.log(`  ${pass} passed, ${fail} failed`);
  return fail;
}
