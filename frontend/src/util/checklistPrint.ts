/**
 * Paper checklists: one A4 sheet listing every group and item of the week once, in one or two
 * columns, with three squares beside each item, one per day, to tick by pen over three days; a
 * note box per day at the foot. The type shrinks until the whole list fits the page. Built as a standalone HTML document and printed through the browser's own
 * dialog (so the current printer, or "Save as PDF").
 */
import type { ChecklistWeek } from '../api/client';
import { addDaysIso } from './isoWeek';
import { dirOf } from './rtl';

const DAY_NAMES = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
export const PRINT_DAYS = 3;

const PT_MM = 0.3528;
/** Items (with group headings) above which the sheet is laid out in two columns. */
const TWO_COLUMNS_FROM = 26;

const esc = (s: string) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

const utc = (iso: string) => new Date(`${iso}T00:00:00Z`);

/** "Friday 20 Nov 2026" for "2026-11-20". */
export function dayLabel(iso: string): string {
  const d = utc(iso);
  return `${DAY_NAMES[(d.getUTCDay() + 6) % 7]} ${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]} ${d.getUTCFullYear()}`;
}

/** The days printed: `start` and the two after it. */
export function printDays(start: string): string[] {
  return Array.from({ length: PRINT_DAYS }, (_, k) => addDaysIso(start, k));
}

type Group = ChecklistWeek['groups'][number];
type Item = Group['items'][number];

/** One table row: a group heading (possibly continued from the column before) or an item. */
export type Row = { kind: 'group'; group: Group; cont: boolean } | { kind: 'item'; group: Group; item: Item };

/** How many columns the sheet uses for this many rows. */
export const columnCount = (rows: number) => (rows > TWO_COLUMNS_FROM ? 2 : 1);

/** A row's rough height in lines: an item wraps by its length, a heading counts a little more. */
function weight(r: Row, perLine: number): number {
  if (r.kind === 'group') return 1.2;
  return Math.max(1, Math.ceil(r.item.text.length / perLine));
}

/**
 * The rows split into `cols` columns of about equal height. A group broken at a column's end
 * repeats its heading, marked as continued, at the top of the next.
 */
export function splitColumns(week: Pick<ChecklistWeek, 'groups'>, cols: number): Row[][] {
  const all: Row[] = [];
  for (const g of week.groups) {
    if (!g.items.length) continue;
    all.push({ kind: 'group', group: g, cont: false });
    for (const item of g.items) all.push({ kind: 'item', group: g, item });
  }
  if (cols <= 1) return [all];
  const perLine = cols > 1 ? 34 : 75;
  const total = all.reduce((n, r) => n + weight(r, perLine), 0);
  const out: Row[][] = [[]];
  let acc = 0;
  for (let k = 0; k < all.length; k++) {
    const r = all[k];
    const col = out[out.length - 1];
    const w = weight(r, perLine);
    // Move on once this column holds its share — but never leave a heading alone at its foot.
    const share = (total / cols) * out.length;
    if (out.length < cols && acc + w / 2 > share && col.length > 0) {
      if (col[col.length - 1].kind === 'group') {
        const head = col.pop()!;
        out.push([head]);
      } else {
        out.push(r.kind === 'item' ? [{ kind: 'group', group: r.group, cont: true }] : []);
      }
    }
    out[out.length - 1].push(r);
    acc += w;
  }
  return out;
}

function table(rows: Row[], days: string[], cols: number): string {
  const dayCol = cols > 1 ? 8.5 : 15;
  const head = days
    .map((d) => {
      const [name, num] = dayLabel(d).split(' ');
      return `<th class="day"><b>${name.slice(0, 3)}</b><span>${num}</span></th>`;
    })
    .join('');
  const body = rows
    .map((r) => {
      if (r.kind === 'group') {
        const g = r.group;
        return `<tr class="grp"><th colspan="${2 + PRINT_DAYS}" dir="${dirOf(g.title)}"><span>${esc(g.title)}</span>${
          r.cont ? '<span class="wk">(continued)</span>' : g.weekly ? '<span class="wk">weekly: tick once</span>' : ''
        }</th></tr>`;
      }
      const boxes = r.group.weekly
        ? `<td class="day" colspan="${PRINT_DAYS}"><span class="box"></span></td>`
        : Array.from({ length: PRINT_DAYS }, () => '<td class="day"><span class="box"></span></td>').join('');
      return `<tr><td class="code">${r.group.code}.${r.item.code}</td><td class="t" dir="${dirOf(r.item.text)}">${esc(r.item.text)}</td>${boxes}</tr>`;
    })
    .join('');
  return `<table><colgroup><col style="width:7mm"><col>${`<col style="width:${dayCol}mm">`.repeat(PRINT_DAYS)}</colgroup>
<thead><tr><th></th><th class="item">Item</th>${head}</tr></thead><tbody>${body}</tbody></table>`;
}

/**
 * A starting font size for the sheet; the print frame then measures the real layout and shrinks
 * it until the sheet fits one page (see FIT_SCRIPT), so this only needs to be in the right range.
 */
export function fitFontSize(week: Pick<ChecklistWeek, 'groups'>): number {
  const n = week.groups.reduce((k, g) => k + (g.items.length ? g.items.length + 1 : 0), 0);
  const perCol = n / columnCount(n);
  // ~230 mm of table height; a row is about 1.3 lines plus 1 mm of padding and rule.
  for (let fs = 11; fs > 6; fs -= 0.25) if (perCol * (fs * 1.3 * PT_MM + 1) <= 230) return fs;
  return 6;
}

/**
 * Runs in the printed document: shrinks the type until the sheet's content fits one A4 page
 * (277 mm high inside the margins), then lets the notes boxes take what is left.
 */
const FIT_SCRIPT = `
(function () {
  function fit() {
    var sheet = document.querySelector('.sheet');
    var ruler = document.createElement('div');
    ruler.style.height = '275mm';
    document.body.appendChild(ruler);
    var limit = ruler.getBoundingClientRect().height;
    ruler.remove();
    sheet.classList.add('measure');
    var fs = parseFloat(getComputedStyle(document.body).fontSize) * 0.75;
    for (var k = 0; k < 40 && sheet.getBoundingClientRect().height > limit && fs > 5; k++) {
      fs -= 0.25;
      document.body.style.fontSize = fs + 'pt';
    }
    sheet.classList.remove('measure');
    document.body.setAttribute('data-fitted', String(fs));
  }
  fit();
  if (document.fonts && document.fonts.ready) document.fonts.ready.then(fit);
})();`;

/** The whole printable page for the days from `start`, with the groups and items of `week`. */
export function checklistPrintHtml(week: ChecklistWeek, start: string, printedOn = new Date()): string {
  const fs = fitFontSize(week);
  const days = printDays(start);
  const groups = week.groups.filter((g) => g.items.length);
  const total = groups.reduce((n, g) => n + g.items.length, 0);
  const cols = columnCount(total + groups.length);
  const tables = splitColumns(week, cols).map((rows) => `<div class="col">${table(rows, days, cols)}</div>`).join('');
  const notes = days.map((d) => `<div class="note"><b>${esc(dayLabel(d))}</b></div>`).join('');
  const printed = `${printedOn.getFullYear()}-${String(printedOn.getMonth() + 1).padStart(2, '0')}-${String(printedOn.getDate()).padStart(2, '0')}`;
  return `<!doctype html>
<html><head><meta charset="utf-8"><title>Checklists ${esc(days[0])} to ${esc(days[PRINT_DAYS - 1])}</title>
<style>
@page { size: A4 portrait; margin: 10mm; }
* { box-sizing: border-box; }
html, body { margin: 0; padding: 0; }
body { font-family: "Noto Sans", "Noto Naskh Arabic", "DejaVu Sans", Arial, sans-serif; font-size: ${fs}pt; color: #000;
  -webkit-print-color-adjust: exact; print-color-adjust: exact; }
.sheet { width: 190mm; display: flex; flex-direction: column; height: 275mm; overflow: hidden; }
.sheet.measure { height: auto; overflow: visible; }
.sheet.measure .notes { flex: none; height: 16mm; }
.top { display: flex; justify-content: space-between; align-items: flex-end; border-bottom: 0.5mm solid #000;
  padding-bottom: 1mm; margin-bottom: 2mm; }
.top h1 { font-size: 13pt; margin: 0; }
.top .sub { font-size: 8.5pt; color: #333; }
.top .meta { font-size: 7.5pt; color: #555; text-align: right; }
.cols { display: flex; gap: 5mm; align-items: flex-start; }
.col { flex: 1 1 0; min-width: 0; }
table { width: 100%; border-collapse: collapse; table-layout: fixed; }
th, td { padding: 0.35mm 0.8mm; border-bottom: 0.15mm solid #c8c8c8; vertical-align: middle; line-height: 1.25; }
thead th { border-bottom: 0.35mm solid #000; font-weight: normal; padding-bottom: 0.6mm; }
thead th.item { text-align: start; font-weight: bold; }
td.code { color: #888; font-size: 0.75em; white-space: nowrap; overflow: hidden; }
td.t { text-align: start; overflow-wrap: anywhere; }
th.day, td.day { text-align: center; padding-left: 0; padding-right: 0; }
th.day b, th.day span { display: block; line-height: 1.1; }
th.day span { font-size: 0.8em; color: #444; }
tr.grp th { text-align: start; background: #ececec; font-weight: bold; padding-top: 0.6mm; border-bottom: 0.25mm solid #777; }
tr.grp .wk { font-weight: normal; font-size: 0.78em; color: #555; margin-inline-start: 2mm; }
.box { display: inline-block; width: 1em; height: 1em; border: 0.3mm solid #000; border-radius: 0.4mm; vertical-align: middle; }
.notes { flex: 1; display: flex; gap: 3mm; margin-top: 3mm; min-height: 16mm; }
.note { flex: 1; border: 0.3mm solid #000; border-radius: 1mm; padding: 0.8mm 1.5mm; font-size: 7.5pt;
  background-image: linear-gradient(to bottom, transparent 5.8mm, #ccc 5.8mm, #ccc 6mm, transparent 6mm);
  background-size: 100% 6mm; background-repeat: repeat-y; background-position: 0 1mm; }
.note b { background: #fff; padding-right: 1mm; }
</style></head>
<body><div class="sheet">
<div class="top">
  <div><h1>Checklists</h1><div class="sub">${esc(dayLabel(days[0]))} – ${esc(dayLabel(days[PRINT_DAYS - 1]))} · Week ${week.week}</div></div>
  <div class="meta">${total} items in ${groups.length} groups · printed ${printed}</div>
</div>
<div class="cols">${tables}</div>
<div class="notes">${notes}</div>
</div>
<script>${FIT_SCRIPT}</script>
</body></html>`;
}

/**
 * Prints `html` with the browser's dialog, from a hidden frame so the app's own page and layout
 * stay as they are. The frame goes once the dialog closes.
 */
export function printHtml(html: string): void {
  const frame = document.createElement('iframe');
  frame.setAttribute('aria-hidden', 'true');
  // Off-screen but full A4 width, so the document's fit step measures the real layout.
  Object.assign(frame.style, { position: 'fixed', left: '-10000px', top: '0', width: '210mm', height: '297mm', border: '0' });
  document.body.appendChild(frame);
  const win = frame.contentWindow;
  const doc = win?.document;
  if (!win || !doc) {
    frame.remove();
    return;
  }
  doc.open();
  doc.write(html);
  doc.close();
  const cleanup = () => setTimeout(() => frame.remove(), 1000);
  win.addEventListener('afterprint', cleanup, { once: true });
  // Let fonts settle before the dialog takes the page's picture.
  const go = () => {
    win.focus();
    win.print();
  };
  if (doc.fonts?.ready) void doc.fonts.ready.then(() => setTimeout(go, 150));
  else setTimeout(go, 200);
  // A browser that never fires afterprint still drops the frame eventually.
  setTimeout(() => frame.isConnected && frame.remove(), 10 * 60_000);
}
