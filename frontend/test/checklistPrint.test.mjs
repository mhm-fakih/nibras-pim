import { checklistPrintHtml, dayLabel, fitFontSize, printDays, splitColumns } from '../src/util/checklistPrint';

/** The paper checklists: day labels, three days across a week end, escaping, shrinking to fit. */
export default function run() {
  let fail = 0;
  const check = (name, got, want) => {
    const ok = JSON.stringify(got) === JSON.stringify(want);
    console.log(ok ? '  ok  ' : '  FAIL', name, ok ? '' : `\n        got:  ${JSON.stringify(got)}\n        want: ${JSON.stringify(want)}`);
    if (!ok) fail++;
  };
  const item = (text) => ({ code: 1, text, marks: [], uid: text, notes: [] });
  const week = (groups) => ({ id: '2026-W47', year: 2026, week: 47, monday: '2026-11-16', sunday: '2026-11-22', groups, carriedFrom: null });

  check('day label', dayLabel('2026-11-20'), 'Friday 20 Nov 2026');
  check('three days over the week end', printDays('2026-11-21'), ['2026-11-21', '2026-11-22', '2026-11-23']);
  const small = week([{ code: 1, title: 'Morning', weekly: false, uid: 'g', items: [item('Read <b>'), item('قراءة القرآن')] }]);
  check('a short list prints at full size', fitFontSize(small), 11);
  const big = week(Array.from({ length: 12 }, (_, g) => ({
    code: g, title: `G${g}`, weekly: false, uid: `g${g}`,
    items: Array.from({ length: 8 }, (_, i) => item(`A rather long checklist item number ${i} that wraps`)),
  })));
  const fs = fitFontSize(big);
  check('a long list starts smaller', fs < 11 && fs >= 6, true);
  const cols = splitColumns(big, 2);
  check('a long list splits into two columns', cols.length, 2);
  check('no item is lost or doubled', cols.flat().filter((r) => r.kind === 'item').length, 96);
  check('the columns are about even', Math.abs(cols[0].length - cols[1].length) <= 10, true);
  check('the second column starts with a heading', cols[1][0].kind, 'group');
  check('a long list prints in two tables', (checklistPrintHtml(big, '2026-11-20').match(/<table>/g) || []).length, 2);
  const html = checklistPrintHtml(small, '2026-11-20');
  check('each item once, with three day squares', (html.match(/<td class="day"><span class="box">/g) || []).length, 6);
  check('three day headings', (html.match(/<th class="day">/g) || []).length, 3);
  check('text is escaped', html.includes('Read &lt;b&gt;'), true);
  check('Arabic items are right-to-left', html.includes('<td class="t" dir="rtl">'), true);
  const wk = checklistPrintHtml(week([{ ...small.groups[0], weekly: true }]), '2026-11-20');
  check('a weekly item has one square across the days', (wk.match(/colspan="3"><span class="box">/g) || []).length, 2);
  return fail;
}
