import { addDaysIso, mondayOfWeekId, shiftWeek, weekIdOf } from '../src/util/isoWeek';

/** The checklist page's ISO weeks: naming, Mondays, stepping across year ends. */
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

  check('a Wednesday', weekIdOf(new Date(2026, 10, 11)), '2026-W46');
  check('a Sunday ends its week', weekIdOf(new Date(2026, 10, 15)), '2026-W46');
  check('1 Jan 2027 (Friday) is in 2026-W53', weekIdOf(new Date(2027, 0, 1)), '2026-W53');
  check('29 Dec 2025 (Monday) is in 2026-W01', weekIdOf(new Date(2025, 11, 29)), '2026-W01');
  check('Monday of 2026-W46', mondayOfWeekId('2026-W46'), '2026-11-09');
  check('Monday of 2026-W01', mondayOfWeekId('2026-W01'), '2025-12-29');
  check('next after W53', shiftWeek('2026-W53', 1), '2027-W01');
  check('previous before W01', shiftWeek('2027-W01', -1), '2026-W53');
  check('days across a month', addDaysIso('2026-10-30', 3), '2026-11-02');
  return fail;
}
