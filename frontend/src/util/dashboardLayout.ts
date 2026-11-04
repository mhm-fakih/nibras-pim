/**
 * Where each dashboard region sits in the grid, computed here rather than left to the browser's
 * auto-placement, so the view page knows the rows — to size them, and to put a draggable separator
 * exactly where two stacked components meet.
 *
 * The placement is CSS grid's own "sparse" auto-placement (row by row, a cursor that never moves
 * back), so a dashboard lands where it always did; the page then pins each region to its cell.
 */

export interface Placed {
  row: number;
  col: number;
  rowSpan: number;
  colSpan: number;
}

export function placeRegions(spans: { colSpan: number; rowSpan: number }[], columns: number): { placed: Placed[]; rows: number } {
  const cols = Math.max(1, columns);
  const taken: boolean[][] = [];
  const free = (r: number, c: number) => !taken[r]?.[c];
  const placed: Placed[] = [];
  let row = 0;
  let col = 0;
  for (const s of spans) {
    const cs = Math.min(Math.max(1, s.colSpan), cols);
    const rs = Math.max(1, s.rowSpan);
    for (;;) {
      if (col + cs > cols) {
        row++;
        col = 0;
        continue;
      }
      let fits = true;
      for (let r = row; r < row + rs && fits; r++) for (let c = col; c < col + cs && fits; c++) fits = free(r, c);
      if (fits) break;
      col++;
    }
    for (let r = row; r < row + rs; r++) {
      taken[r] = taken[r] ?? [];
      for (let c = col; c < col + cs; c++) taken[r][c] = true;
    }
    placed.push({ row, col, rowSpan: rs, colSpan: cs });
    col += cs;
  }
  const rows = placed.reduce((m, p) => Math.max(m, p.row + p.rowSpan), 0);
  return { placed, rows };
}

/** A stretch of the boundary under row `row`, columns `fromCol`..`toCol` inclusive. */
export interface RowSeparator {
  row: number;
  fromCol: number;
  toCol: number;
}

/**
 * The separators between rows: under row r, in every column where the cell above and the cell
 * below belong to different regions (one may be empty) — not across a region that spans both rows,
 * which a drag there would only cut through. Neighbouring columns join into one stretch.
 */
export function rowSeparators(placed: Placed[], rows: number, columns: number): RowSeparator[] {
  const owner: number[][] = Array.from({ length: rows }, () => Array(columns).fill(-1));
  placed.forEach((p, i) => {
    for (let r = p.row; r < p.row + p.rowSpan; r++) for (let c = p.col; c < p.col + p.colSpan; c++) owner[r][c] = i;
  });
  const out: RowSeparator[] = [];
  for (let r = 0; r < rows - 1; r++) {
    let start = -1;
    for (let c = 0; c <= columns; c++) {
      const a = c < columns ? owner[r][c] : -1;
      const b = c < columns ? owner[r + 1][c] : -1;
      const cut = c < columns && a !== b && (a >= 0 || b >= 0);
      if (cut && start < 0) start = c;
      if (!cut && start >= 0) {
        out.push({ row: r, fromCol: start, toCol: c - 1 });
        start = -1;
      }
    }
  }
  return out;
}

/** Stored weights → one per row (missing or bad entries are 1). */
export function parseWeights(stored: string | null | undefined, count: number): number[] {
  const parts = (stored ?? '').split(',').map((p) => Number(p.trim()));
  return Array.from({ length: count }, (_, i) => (parts[i] > 0 ? parts[i] : 1));
}

/**
 * New weights for rows `i` and `i+1` after the boundary between them moved by `dy` px, given the
 * rows' current heights in px. The pair keeps its combined weight (other rows don't move), and
 * neither row goes below `minPx`.
 */
export function dragPair(weights: number[], px: number[], i: number, dy: number, minPx: number): number[] {
  const pairPx = px[i] + px[i + 1];
  const pairW = weights[i] + weights[i + 1];
  if (!(pairPx > 0)) return weights;
  const lo = Math.min(minPx, pairPx / 2);
  const top = Math.min(pairPx - lo, Math.max(lo, px[i] + dy));
  const w = (pairW * top) / pairPx;
  return weights.map((x, k) => (k === i ? w : k === i + 1 ? pairW - w : x));
}
