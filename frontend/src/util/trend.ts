// The dashboard ticker's market-style arrows (components/IndicatorTicker.tsx).

/** Market-style movement of an indicator between its previous and its latest value. */
export type Move = 'up2' | 'up' | 'same' | 'down' | 'down2' | 'none';

export interface TickerConfig {
  /** A change of at least this many percent shows a double arrow (default 10). */
  threshold?: number;
  /** Only these categories (comma-separated, case-insensitive); blank = every monitored indicator. */
  categories?: string;
}

export const DEFAULT_TICKER_THRESHOLD = 10;

/**
 * The move from `previous` to `value`: same when equal, a double arrow when the change is at least
 * `threshold` percent of the previous value (or from 0 to anything), a single arrow otherwise;
 * `none` until the indicator has two values to compare.
 */
export function moveOf(value: number | null | undefined, previous: number | null | undefined,
                       threshold = DEFAULT_TICKER_THRESHOLD): Move {
  if (value == null || previous == null) return 'none';
  const delta = value - previous;
  if (delta === 0) return 'same';
  const pct = previous === 0 ? Infinity : (Math.abs(delta) / Math.abs(previous)) * 100;
  const big = pct >= threshold;
  if (delta > 0) return big ? 'up2' : 'up';
  return big ? 'down2' : 'down';
}

