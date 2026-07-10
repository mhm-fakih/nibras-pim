/**
 * Custom tool pages that can be enabled/disabled in Settings (Settings + Help are always on).
 * `key` matches the route path without its leading slash.
 */
export interface ToolPage {
  key: string;
  label: string;
}

export const TOGGLEABLE_PAGES: ToolPage[] = [
  { key: 'calendar', label: 'Calendar' },
  { key: 'kanban', label: 'Kanban' },
  { key: 'bookmarks', label: 'All bookmarks' },
  { key: 'indicators', label: 'Indicators' },
  { key: 'admin', label: 'Admin' },
  { key: 'batch', label: 'Batch entry' },
  { key: 'import-files', label: 'Import files' },
  { key: 'import', label: 'Import notes' },
  { key: 'images', label: 'Image renamer' },
  { key: 'sites', label: 'Static website' },
  { key: 'audit', label: 'Audit log' },
];

export const pageSettingKey = (key: string) => `page.${key}.enabled`;

/** All the keys to fetch in one go. */
export const PAGE_SETTING_KEYS = TOGGLEABLE_PAGES.map((p) => pageSettingKey(p.key));

/** A page is enabled unless its setting is explicitly "false". */
export function pageEnabled(settings: Record<string, string> | undefined, key: string): boolean {
  return settings?.[pageSettingKey(key)] !== 'false';
}
