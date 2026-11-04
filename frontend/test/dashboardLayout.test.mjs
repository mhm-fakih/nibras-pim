import { dragPair, parseWeights, placeRegions, rowSeparators } from '../src/util/dashboardLayout';

/** Dashboard placement (must match CSS grid's own auto-placement) and the row separators. */
export default function run() {
  let pass = 0;
  let fail = 0;
  const check = (name, got, want) => {
    if (JSON.stringify(got) === JSON.stringify(want)) {
      pass++;
      console.log('  ok  ', name);
    } else {
      fail++;
      console.log('  FAIL', name, '\n        got: ', JSON.stringify(got), '\n        want:', JSON.stringify(want));
    }
  };
  const r = (colSpan = 1, rowSpan = 1) => ({ colSpan, rowSpan });
  const cells = (p) => p.map((x) => [x.row, x.col]);

  // A calendar spanning two rows beside two stacked components.
  const cal = placeRegions([r(1, 2), r(), r()], 2);
  check('calendar + two stacked: placement', cells(cal.placed), [[0, 0], [0, 1], [1, 1]]);
  check('calendar + two stacked: rows', cal.rows, 2);
  check('separator only in the column where two meet', rowSeparators(cal.placed, cal.rows, 2), [{ row: 0, fromCol: 1, toCol: 1 }]);

  const grid = placeRegions([r(), r(), r(), r()], 2);
  check('2x2: one separator across both columns', rowSeparators(grid.placed, grid.rows, 2), [{ row: 0, fromCol: 0, toCol: 1 }]);

  // Sparse placement: a too-wide item moves to the next row and the cursor never goes back.
  const sparse = placeRegions([r(1), r(2), r(1)], 3);
  check('sparse auto-placement', cells(sparse.placed), [[0, 0], [0, 1], [1, 0]]);
  const back = placeRegions([r(2), r(2), r(1)], 3);
  check('cursor does not backfill', cells(back.placed), [[0, 0], [1, 0], [1, 2]]);
  check('a span wider than the grid is clamped', placeRegions([r(5)], 2).placed[0].colSpan, 2);

  const lone = placeRegions([r(), r(), r()], 2); // third alone on row 2, empty cell beside it
  check('a region over an empty cell still gets a separator', rowSeparators(lone.placed, lone.rows, 2), [
    { row: 0, fromCol: 0, toCol: 1 },
  ]);

  check('weights padded and cleaned', parseWeights('2,x,-1', 4), [2, 1, 1, 1]);

  const w = dragPair([1, 1, 1], [300, 300, 300], 0, 100, 120);
  check('drag moves weight within the pair only', w.map((x) => Math.round(x * 100) / 100), [1.33, 0.67, 1]);
  check('pair total kept', Math.round((w[0] + w[1]) * 1000) / 1000, 2);
  const clamp = dragPair([1, 1], [300, 300], 0, 1000, 120);
  check('clamped at the minimum height', Math.round((clamp[1] / 2) * 600), 120);

  console.log(`  ${pass} passed, ${fail} failed`);
  return fail;
}
