import { useCallback, useState } from 'react';

/**
 * The font size long-form text (a resource's full text) is read at, as a multiple of the normal
 * size. Set with the slider on the read page and kept per browser, so the detail pane and the next
 * record open at the same size.
 */
const KEY = 'nibras.readingScale';
export const READING_SCALE_MIN = 0.9;
export const READING_SCALE_MAX = 1.8;
export const READING_SCALE_DEFAULT = 1.1;

function load(): number {
  try {
    const v = Number(localStorage.getItem(KEY));
    if (Number.isFinite(v) && v >= READING_SCALE_MIN && v <= READING_SCALE_MAX) return v;
  } catch {
    // Storage blocked (private window, site data off): the default size still works.
  }
  return READING_SCALE_DEFAULT;
}

export function useReadingScale(): [number, (v: number) => void] {
  const [scale, setScale] = useState(load);
  const set = useCallback((raw: number) => {
    const v = Math.round(raw * 100) / 100;   // slider steps add float noise (1.3000000000000003)
    setScale(v);
    try {
      localStorage.setItem(KEY, String(v));
    } catch {
      // Not kept for next time; this page still uses it.
    }
  }, []);
  return [scale, set];
}
