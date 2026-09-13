/**
 * Counts with thousand separators — "22,385", not "22385".
 *
 * A five-digit run of bare digits has to be counted before it can be read, and these numbers exist
 * precisely to be read at a glance ("how much is left of this?").
 *
 * `en-US` rather than the browser's locale, to match the mobile galleries: the same bank is read on
 * both, and an Arabic-locale browser would otherwise group the identical number differently — and
 * in Arabic-Indic digits — from the phone showing the same card.
 */
export const grouped = (n: number): string => n.toLocaleString('en-US');
