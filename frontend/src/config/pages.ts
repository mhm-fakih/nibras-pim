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
  { key: 'dashboards', label: 'Dashboards' },
  { key: 'bookmarks', label: 'All bookmarks' },
  { key: 'indicators', label: 'Indicators' },
  { key: 'insights', label: 'Insights' },
  { key: 'sprints', label: 'Sprints' },
  { key: 'parameters', label: 'Parameters' },
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

/**
 * Toggleable *features* — pieces of UI that aren't whole routes, so [TOGGLEABLE_PAGES] can't
 * express them. Same storage shape and same default-on rule, a separate namespace so a feature key
 * can never collide with a route name.
 */
export interface ToolFeature {
  key: string;
  label: string;
  description: string;
}

export const TOGGLEABLE_FEATURES: ToolFeature[] = [
  {
    key: 'rss',
    label: 'RSS feeds',
    description: 'The per-module “RSS (bookmarked)” feed link in the sidebar.',
  },
  {
    key: 'moduleReports',
    label: 'Standard reports per module',
    description:
      'The built-in report shortcuts in the sidebar (Top priority, Without department, …). Your own saved searches are unaffected.',
  },
  {
    key: 'codename',
    label: 'Release codename',
    description:
      'The release’s nickname — “Dastgheib” — beside the version number in the sidebar header and the status bar. The version itself is always shown; this hides only the name.',
  },
  {
    key: 'resourceExtras',
    label: 'Resource detail fields',
    description:
      'Full text, Highlights, Comments, URL and Read-on on the Resources form and record. Turning them off only hides them — nothing already written is changed or lost.',
  },
  {
    key: 'resourceFromUrl',
    label: 'New resource from a URL',
    description:
      'The link button beside “New” on Resources: paste an article’s address and the server reads the page to fill in the form, cover image included.',
  },
];

export const featureSettingKey = (key: string) => `feature.${key}.enabled`;

export const FEATURE_SETTING_KEYS = TOGGLEABLE_FEATURES.map((f) => featureSettingKey(f.key));

/** A feature is enabled unless its setting is explicitly "false". */
export function featureEnabled(settings: Record<string, string> | undefined, key: string): boolean {
  return settings?.[featureSettingKey(key)] !== 'false';
}
