import { Lookup } from '../api/client';

/**
 * Shared pastel palette for auto-colouring lookup chips that have no explicit style yet.
 * Kept in sync with the backend (LookupAdminService.PALETTE) so the colour a chip shows
 * before "Fill empty colours" matches what gets persisted afterwards.
 */
export const LOOKUP_PALETTE = [
  '#ffcdd2', '#f8bbd0', '#e1bee7', '#d1c4e9', '#c5cae9', '#bbdefb', '#b3e5fc',
  '#b2ebf2', '#b2dfdb', '#c8e6c9', '#dcedc8', '#fff9c4', '#ffe0b2', '#ffccbc',
];

/** Deterministic palette colour for a lookup name (same formula on the backend). */
export function paletteColor(name?: string | null): string {
  const s = (name ?? '').toLowerCase();
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h + s.charCodeAt(i)) % LOOKUP_PALETTE.length;
  return LOOKUP_PALETTE[h];
}

/**
 * Chip colours for a lookup value. Prefers the explicit (legacy inline-CSS) `style` column;
 * when empty, falls back to a deterministic pastel from {@link paletteColor} so every
 * status / type / department / context chip is colour-coded.
 */
export function lookupStyle(lookup?: Lookup): { backgroundColor?: string; color?: string } {
  if (lookup?.style) {
    const style = lookup.style;
    const bg = /background(?:-color)?\s*:\s*([^;!]+)/i.exec(style);
    const fg = /(?:^|;)\s*color\s*:\s*([^;!]+)/i.exec(style);
    const out: { backgroundColor?: string; color?: string } = {};
    if (bg) out.backgroundColor = bg[1].trim();
    if (fg) out.color = fg[1].trim();
    if (out.backgroundColor) return out;
  }
  if (!lookup) return {};
  return { backgroundColor: paletteColor(lookup.name), color: 'rgba(0,0,0,0.82)' };
}

/** Back-compat alias (was status-only; now applies to any lookup). */
export const statusStyle = lookupStyle;
