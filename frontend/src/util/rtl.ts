// Text-direction detection based on the *content*, not any stored `language` field (which may hold a
// wrong value). Mirrors the browser's `dir="auto"` heuristic: the direction is decided by the first
// strong-directional character, so neutral leading characters (a "★ " bookmark prefix, quotes, digits,
// punctuation) are skipped.

const RTL_CHAR = /[֐-׿؀-ۿ܀-ݏݐ-ݿࢠ-ࣿיִ-﷿ﹰ-﻿]/;
const STRONG_LTR_CHAR = /[A-Za-zÀ-ʯͰ-ԯ]/;

/** True when the first strong-directional character is RTL (Arabic/Hebrew/…). */
export function isRtlText(s?: string | null): boolean {
  if (!s) return false;
  for (const ch of s) {
    if (RTL_CHAR.test(ch)) return true;
    if (STRONG_LTR_CHAR.test(ch)) return false;
  }
  return false;
}

/** `'rtl'` / `'ltr'` for a `dir` attribute, from the text content. */
export function dirOf(s?: string | null): 'rtl' | 'ltr' {
  return isRtlText(s) ? 'rtl' : 'ltr';
}
