import { ENTITY_BY_KEY } from './entities';

export interface ReportDef {
  key: string;
  label: string;
  /** Query params applied to the module list. */
  params: Record<string, string>;
  /** Whether this report makes sense for the given module. */
  appliesTo: (moduleKey: string) => boolean;
}

const hasField = (moduleKey: string, field: string) =>
  (ENTITY_BY_KEY[moduleKey]?.fields ?? []).some((f) => f.name === field);

const always = () => true;

export const REPORTS: ReportDef[] = [
  { key: 'bookmarked', label: '★ Bookmarked', params: { bookmarked: 'true' }, appliesTo: always },
  {
    key: 'urgent',
    label: '🚩 Urgent',
    params: { urgent: 'true' },
    appliesTo: (m) => hasField(m, 'urgent'),
  },
  {
    key: 'noStatus',
    label: 'No status',
    params: { noStatus: 'true' },
    appliesTo: (m) => Boolean(ENTITY_BY_KEY[m]?.hasStatus),
  },
  {
    key: 'noType',
    label: 'No type',
    params: { noType: 'true' },
    appliesTo: (m) => Boolean(ENTITY_BY_KEY[m]?.hasType),
  },
  { key: 'noDepartment', label: 'No department', params: { noDepartment: 'true' }, appliesTo: always },
  { key: 'noPriority', label: 'No priority', params: { noPriority: 'true' }, appliesTo: always },
  { key: 'hasTags', label: 'With tags', params: { hasTags: 'true' }, appliesTo: always },
  {
    key: 'overdue',
    label: 'Overdue',
    params: { overdue: 'true' },
    appliesTo: (m) => hasField(m, 'endDate'),
  },
  {
    key: 'dueSoon',
    label: 'Due soon (7d)',
    params: { dueSoon: 'true' },
    appliesTo: (m) => hasField(m, 'endDate'),
  },
  { key: 'highPriority', label: 'High priority (≥3)', params: { highPriority: 'true' }, appliesTo: always },
  { key: 'recentCreated', label: 'Recently created', params: { recentCreated: 'true' }, appliesTo: always },
  { key: 'recentUpdated', label: 'Recently updated', params: { recentUpdated: 'true' }, appliesTo: always },
];

export function reportsFor(moduleKey: string): ReportDef[] {
  return REPORTS.filter((r) => r.appliesTo(moduleKey));
}

/** Friendly label for whichever report param is active in a query string. */
export function activeReportLabel(search: URLSearchParams): string | null {
  for (const r of REPORTS) {
    const [k, v] = Object.entries(r.params)[0];
    if (search.get(k) === v) return r.label;
  }
  return null;
}
