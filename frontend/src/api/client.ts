import axios from 'axios';

/** Base path the app is served under: '/' normally, or e.g. '/art/' under a Tomcat context. Read at
 * RUNTIME from `<base href>` (which the backend rewrites to the servlet context path), so ONE build
 * works under any context. Always ends with '/'. Drives the axios baseURL, /api & /feeds URLs, the
 * 401 redirect and the router basename. */
export const BASE = new URL(document.baseURI).pathname;
export const http = axios.create({ baseURL: `${BASE}api` });

// ---- Auth -----------------------------------------------------------------

export type Role = 'ADMIN' | 'DATA_ENTRY' | 'PARAMETERS_DATA_ENTRY' | 'READ_ONLY';

/** Capability keys (must match backend com.nibras.pim.security.Permissions). */
export type Permission =
  | 'content.write'
  | 'params.write'
  | 'users.manage'
  | 'tools.admin'
  | 'tools.settings'
  | 'tools.audit';

export interface AuthUser {
  username: string;
  displayName: string | null;
  role: Role;
  permissions: Permission[];
  mustChangePassword: boolean;
}

const TOKEN_KEY = 'nibras.token';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

// Attach the bearer token to every request.
http.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers = config.headers ?? {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// On 401 (expired/invalid token) drop the token and bounce to the login screen — unless the failing
// request *was* the login attempt (which surfaces its own error to the form).
http.interceptors.response.use(
  (r) => r,
  (error) => {
    const url: string = error?.config?.url ?? '';
    if (error?.response?.status === 401 && !url.includes('/auth/login')) {
      setToken(null);
      if (!window.location.pathname.startsWith(`${BASE}login`)) {
        window.location.assign(`${BASE}login`);
      }
    }
    return Promise.reject(error);
  },
);

export async function login(username: string, password: string): Promise<AuthUser> {
  const { data } = await http.post('/auth/login', { username, password });
  setToken(data.token);
  return data.user as AuthUser;
}

export async function fetchMe(): Promise<AuthUser> {
  const { data } = await http.get('/auth/me');
  return data as AuthUser;
}

export function logout() {
  setToken(null);
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<AuthUser> {
  const { data } = await http.post('/auth/password', { currentPassword, newPassword });
  return data as AuthUser;
}

// ---- Permission matrix (admin) --------------------------------------------

export interface PermissionMatrix {
  /** Full vocabulary of assignable keys (columns). */
  permissions: Permission[];
  /** Role name → the keys it currently holds. */
  roles: Record<Role, Permission[]>;
}

export async function getPermissionMatrix(): Promise<PermissionMatrix> {
  const { data } = await http.get('/permissions');
  return data;
}

export async function setRolePermissions(role: Role, permissions: Permission[]): Promise<PermissionMatrix> {
  const { data } = await http.put(`/permissions/${role}`, { permissions });
  return data;
}

// ---- User management (admin) ----------------------------------------------

export interface ManagedUser {
  id: number;
  username: string;
  displayName: string | null;
  role: Role;
  enabled: boolean;
  dateCreated: string;
  lastUpdated: string;
}

export interface SaveUserBody {
  username: string;
  displayName?: string | null;
  role: Role;
  enabled?: boolean;
  password?: string;
}

export async function listUsers(): Promise<ManagedUser[]> {
  const { data } = await http.get('/users');
  return data;
}

export async function createUser(body: SaveUserBody): Promise<ManagedUser> {
  const { data } = await http.post('/users', body);
  return data;
}

export async function updateUser(id: number, body: SaveUserBody): Promise<ManagedUser> {
  const { data } = await http.put(`/users/${id}`, body);
  return data;
}

export async function deleteUser(id: number): Promise<void> {
  await http.delete(`/users/${id}`);
}

export interface Lookup {
  id: number;
  name: string;
  code?: string;
  style?: string;
}

export interface TagRef {
  id: number;
  name: string;
}

/** All entity DTOs share these; the rest of the fields vary per entity. */
export interface EntityRecord {
  id: number;
  version: number;
  displayTitle: string;
  dateCreated: string;
  lastUpdated: string;
  bookmarked?: boolean;
  status?: Lookup;
  type?: Lookup;
  context?: Lookup;
  department?: Lookup;
  course?: Lookup;
  book?: Lookup;
  goal?: Lookup;
  goalId?: number;
  tags?: TagRef[];
  hasCover?: boolean;
  urgent?: boolean;
  orderNumber?: number;
  recurring?: boolean;
  recurrencePreset?: string;
  recurrenceInterval?: number;
  recurrenceCron?: string;
  fixedSchedule?: boolean;
  [key: string]: unknown;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface DayCount {
  date: string;
  count: number;
}

export interface ListParams {
  q?: string;
  createdOn?: string;
  /** Date-aware "day" filter (journals→startDate, planners→startDate, else dateCreated). */
  onDay?: string;
  /** Inclusive dateCreated range, e.g. "records created in the last 3 months". */
  from?: string;
  to?: string;
  bookmarked?: boolean;
  urgent?: boolean;
  dashboard?: boolean;
  statusId?: number;
  typeId?: number;
  tagId?: number;
  goalId?: number;
  page?: number;
  size?: number;
  /** Spring Pageable sort, e.g. "lastUpdated,desc" — overrides the endpoint's default (dateCreated
   * desc) when the "most recent" that actually matters is last-touched, not first-created. */
  sort?: string;
  // report presets
  noStatus?: boolean;
  noType?: boolean;
  noDepartment?: boolean;
  noPriority?: boolean;
  hasTags?: boolean;
  overdue?: boolean;
  dueSoon?: boolean;
  highPriority?: boolean;
  recentCreated?: boolean;
  recentUpdated?: boolean;
}

/** Report preset query keys (used by the reports panel + list chip). */
export const REPORT_KEYS = [
  'noStatus', 'noType', 'noDepartment', 'noPriority', 'hasTags',
  'overdue', 'dueSoon', 'highPriority', 'recentCreated', 'recentUpdated', 'bookmarked', 'urgent',
] as const;

function clean(params: Record<string, unknown>): Record<string, unknown> {
  return Object.fromEntries(
    Object.entries(params).filter(([, v]) => v !== undefined && v !== '' && v !== null),
  );
}

export async function listEntities(
  path: string,
  params: ListParams,
): Promise<PageResponse<EntityRecord>> {
  const { data } = await http.get(`/${path}`, { params: clean({ ...params }) });
  return data;
}

export async function getEntity(path: string, id: number): Promise<EntityRecord> {
  const { data } = await http.get(`/${path}/${id}`);
  return data;
}

export async function createEntity(path: string, body: unknown): Promise<EntityRecord> {
  const { data } = await http.post(`/${path}`, body);
  return data;
}

export async function updateEntity(
  path: string,
  id: number,
  body: unknown,
): Promise<EntityRecord> {
  const { data } = await http.put(`/${path}/${id}`, body);
  return data;
}

/** "+ Subtask": creates a new Task on the same Goal as `taskId`, ordered right after it. */
export async function createSubtask(taskId: number, summary: string): Promise<EntityRecord> {
  const { data } = await http.post(`/tasks/${taskId}/subtask`, { summary });
  return data;
}

export async function deleteEntity(path: string, id: number): Promise<void> {
  await http.delete(`/${path}/${id}`);
}

export async function getLookups(path: string): Promise<Record<string, Lookup[]>> {
  const { data } = await http.get(`/${path}/lookups`);
  return data;
}

/** GTD contexts (work_location) — for the quick-edit @ picker. */
export async function listContexts(): Promise<Lookup[]> {
  const { data } = await http.get('/contexts');
  return data;
}

/** Generic single setting access. */
export async function getSetting(key: string, def = ''): Promise<string> {
  const { data } = await http.get('/settings/value', { params: { key, def } });
  return data.value;
}
export async function setSetting(key: string, value: string): Promise<void> {
  await http.put('/settings/value', { value }, { params: { key } });
}

/** Fetch several settings at once (unset keys come back as ""). */
export async function getSettingsMulti(keys: string[]): Promise<Record<string, string>> {
  const { data } = await http.get('/settings/multi', { params: { keys: keys.join(',') } });
  return data;
}

// ---- Batch entry (one record per line, command grammar) ---------------------

export interface BatchEntryLine {
  line: number;
  input: string;
  module?: string;
  id?: number;
  error?: string;
}
export interface BatchEntryResult {
  created: number;
  results: BatchEntryLine[];
}

export async function batchEntry(module: string, text: string): Promise<BatchEntryResult> {
  const { data } = await http.post('/batch-entry', { module, text });
  return data;
}

// ---- Related records (via legacy foreign keys) ------------------------------

export interface RelatedItem {
  module: string;
  id: number;
  title: string;
}
export interface RelatedGroup {
  module: string;
  label: string;
  fk: string;
  count: number;
  items: RelatedItem[];
}

export async function getRelated(module: string, id: number): Promise<RelatedGroup[]> {
  const { data } = await http.get(`/related/${module}/${id}`);
  return data;
}

/** Set (or clear, when targetId omitted) a ManyToOne link by id. */
export async function linkRecord(
  module: string,
  id: number,
  field: string,
  targetId?: number,
): Promise<void> {
  await http.post(`/${module}/${id}/link`, null, { params: { field, targetId } });
}

// ---- File/folder import (command-syntax filenames) --------------------------

export interface ImportPlanItem {
  name: string;
  directory: boolean;
  module?: string;
  title?: string;
  operation?: string;
  target?: string;
  error?: string;
}
export interface ImportPreview {
  dir: string;
  items: ImportPlanItem[];
}
export interface ImportFileItem {
  name: string;
  directory: boolean;
  module?: string;
  id?: number;
  movedTo?: string;
  error?: string;
}
export interface ImportFilesResult {
  dir: string;
  imported: number;
  items: ImportFileItem[];
}

export async function previewImportFiles(dir: string, module: string): Promise<ImportPreview> {
  const { data } = await http.get('/import-files', { params: { dir: dir || undefined, module } });
  return data;
}

export async function importFiles(
  dir: string,
  module: string,
  only?: string,
): Promise<ImportFilesResult> {
  const { data } = await http.post('/import-files', { dir: dir || undefined, module, only });
  return data;
}

// ---- Lookup colours (status/type/department/context chip styling) -----------

export interface LookupValue {
  id: number;
  name: string;
  code?: string;
  style?: string;
}
export interface LookupGroup {
  table: string;
  label: string;
  /** Value used when this parameter is left blank on a new record (marked with * in the UI). */
  defaultId?: number | null;
  values: LookupValue[];
}

export async function getLookupGroups(): Promise<LookupGroup[]> {
  const { data } = await http.get('/lookups');
  return data;
}

/** Set one lookup value's chip style (pass '' to clear). */
export async function setLookupStyle(table: string, id: number, style: string): Promise<void> {
  await http.put(`/lookups/${table}/${id}/style`, { style });
}

/** Set (id) or clear (null) the parameter's default value, applied when a record leaves it blank. */
export async function setLookupDefault(table: string, id: number | null): Promise<void> {
  await http.put(`/lookups/${table}/default`, { id });
}

/** Give every still-uncoloured lookup value a deterministic colour. Returns how many were filled. */
export async function fillLookupColors(): Promise<number> {
  const { data } = await http.post('/lookups/fill-colors');
  return data.filled as number;
}

/** Add a new value to a lookup table (e.g. a new resource type). */
export async function createLookupValue(table: string, name: string, code?: string): Promise<LookupValue> {
  const { data } = await http.post(`/lookups/${table}`, { name, code });
  return data;
}

/** Rename a lookup value and/or change its code. */
export async function renameLookupValue(table: string, id: number, name: string, code?: string): Promise<void> {
  await http.put(`/lookups/${table}/${id}`, { name, code });
}

/** Soft-delete a lookup value. */
export async function deleteLookupValue(table: string, id: number): Promise<void> {
  await http.delete(`/lookups/${table}/${id}`);
}

/** Course option for the record course-picker (carries its department for auto-fill). */
export interface CourseOption {
  id: number;
  displayTitle: string;
  code?: string;
  departmentId?: number;
  department?: Lookup;
}

export async function listCourses(): Promise<CourseOption[]> {
  const { data } = await http.get('/courses', { params: { size: 1000, sort: 'summary' } });
  return data.content as CourseOption[];
}

export async function getCalendar(
  from: string,
  to: string,
  entity?: string,
): Promise<DayCount[]> {
  const { data } = await http.get('/calendar', { params: clean({ from, to, entity }) });
  return data;
}

// ---- Calendar events (full-page calendar) ----------------------------------

/** What a page gave up when scraped. Every field may be blank — it prefills a form, not a record. */
export interface ScrapedResource {
  url: string;
  title: string;
  author: string;
  publisher: string;
  shortDescription: string;
  resourceYear: string;
  fullText: string;
  /**
   * Absolute URL of the page's main image, or "" — a candidate cover, not a fetched one. The
   * record doesn't exist yet when this is scraped, so there is nowhere to put the file until it
   * has been saved; see [setCoverFromUrl].
   */
  imageUrl: string;
  /** Non-fatal notes: what couldn't be extracted, and why. */
  warnings: string[];
}

/**
 * Fetch a page server-side and pull Resource fields out of it.
 *
 * A POST rather than a GET because it makes the SERVER issue an outbound request to a URL the user
 * chose — a side effect, and one gated on `content.write` by SecurityConfig precisely because of
 * it. The browser can't do this itself: cross-origin reads of arbitrary pages are blocked.
 */
export async function scrapeUrl(url: string): Promise<ScrapedResource> {
  const { data } = await http.post('/scrape', { url });
  return data;
}

/**
 * Download an image server-side and save it as the record's cover (`cover.<ext>` in repository 1).
 *
 * The scrape's other half: it can only run once the record has an id, and only fills an empty
 * cover slot — the backend refuses rather than replacing a picture that is already there.
 */
export async function setCoverFromUrl(module: string, id: number, url: string): Promise<void> {
  await http.post(`/attachments/${module}/${id}/cover-from-url`, { url });
}

export interface CalendarEvent {
  module: 'journals' | 'planners' | 'sprints';
  /** Null on a virtual occurrence — there is no record behind it yet. See `virtual`. */
  id: number | null;
  title: string;
  description?: string;
  start: string;
  end?: string;
  typeName?: string;
  typeId?: number;
  typeStyle?: string;
  /** Set on every event belonging to a recurring planner series, virtual or not. */
  seriesId?: number | null;
  /** The slot this occurrence stands for: its ORIGINAL start, unchanged by dragging it. */
  recurrenceId?: string | null;
  /**
   * True for a computed occurrence with no row of its own. Anything needing a record id must call
   * `materialiseOccurrence` first — see PlannerSeriesService on the backend for the whole model.
   */
  virtual?: boolean;
}

/**
 * Turns a virtual occurrence of a recurring planner into a real record and returns it. Idempotent:
 * the same slot always resolves to the same record, so it is safe to call on every interaction
 * rather than tracking what has already been materialised.
 */
export async function materialiseOccurrence(
  seriesId: number,
  recurrenceId: string,
): Promise<EntityRecord> {
  const { data } = await http.post('/calendar/occurrences/materialise', { seriesId, recurrenceId });
  return data;
}

/** Removes one occurrence from its series (soft-deletes the row if there is one). */
export async function skipOccurrence(seriesId: number, recurrenceId: string): Promise<void> {
  await http.post('/calendar/occurrences/skip', { seriesId, recurrenceId });
}

export async function getCalendarEvents(
  from: string,
  to: string,
  modules = 'journals,planners,sprints',
  type?: string,
): Promise<CalendarEvent[]> {
  const { data } = await http.get('/calendar/events', { params: clean({ from, to, modules, type }) });
  return data;
}

// ---- Audit trail ------------------------------------------------------------

export interface AuditEntry {
  id: number;
  txnId: string;
  entity: string;
  entityId: number;
  op: 'INSERT' | 'UPDATE' | 'DELETE';
  field?: string;
  oldValue?: string;
  newValue?: string;
  changedAt: string;
  changedBy: string;
}

export interface AuditParams {
  entity?: string;
  entityId?: number;
  op?: string;
  field?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export async function listAudit(params: AuditParams): Promise<PageResponse<AuditEntry>> {
  const { data } = await http.get('/audit', { params: clean({ ...params }) });
  return data;
}

export async function getRecordHistory(entity: string, id: number): Promise<AuditEntry[]> {
  const { data } = await http.get(`/audit/${entity}/${id}`);
  return data;
}

// ---- App modules (settings) -------------------------------------------------

export interface AppModule {
  moduleKey: string;
  label: string;
  code?: string;
  /** Accent colour (hex) shown as the list-row bar. */
  color?: string;
  enabled: boolean;
  sortOrder: number;
  /** Order the module's parameters are handed to the clients (Admin → Synced to clients). */
  syncOrder?: number;
  /** Record folder nests under an id/100 bucket (<code>/<id100>/<id>). */
  bucketBy100?: boolean;
  /** Record folder nests under its type code (<code>/<type>/…/<id>). */
  bucketByType?: boolean;
}

export async function listModules(): Promise<AppModule[]> {
  const { data } = await http.get('/modules');
  return data;
}

export async function updateModule(key: string, body: Partial<AppModule>): Promise<AppModule> {
  const { data } = await http.put(`/modules/${key}`, body);
  return data;
}

// ---- Record files (in repositories 1/2/3) -----------------------------------

export interface RepoFile {
  repo: number;
  name: string;
  directory: boolean;
  size: number;
  modifiedAt: string;
}

/** Files in the record's folder across repositories 1/2/3. */
export async function listRepoFiles(module: string, id: number): Promise<RepoFile[]> {
  const { data } = await http.get(`/attachments/${module}/${id}/files`);
  return data;
}

/** Upload a file into the record's folder in repository `repo` (1/2/3). */
export async function uploadRepoFile(
  module: string,
  id: number,
  repo: number,
  file: File,
): Promise<void> {
  const form = new FormData();
  form.append('file', file);
  await http.post(`/attachments/${module}/${id}/file`, form, { params: { repo } });
}

/** Delete a file from the record's folder in repository `repo`. */
export async function deleteRepoFile(
  module: string,
  id: number,
  repo: number,
  name: string,
): Promise<void> {
  await http.delete(`/attachments/${module}/${id}/file`, { params: { repo, path: name } });
}

/** Direct URL for an anchor/img (bypasses the axios `/api` baseURL). */
export function repoFileUrl(module: string, id: number, repo: number, name: string): string {
  return `${BASE}api/attachments/${module}/${id}/file?repo=${repo}&path=${encodeURIComponent(name)}`;
}

// ---- Saved searches (user-defined reports) ----------------------------------

export interface SavedSearch {
  id?: number;
  module?: string;
  entityName?: string;
  queryType: 'hql' | 'sql' | 'form';
  query: string;
  countQuery?: string;
  summary: string;
  code?: string;
  reportType?: string;
  orderNumber?: string;
  bookmarked?: boolean;
  onMobile?: boolean;
  priority?: number;
  notes?: string;
}

/** Result of running a saved search: rows / mutation count / form params / error. */
export interface SavedSearchRecordRef {
  module: string;
  id: number;
  title: string;
}

export interface SavedSearchResult {
  type: 'rows' | 'mutation' | 'form' | 'error';
  rows?: unknown[][];
  count?: number;
  affected?: number;
  query?: string;
  message?: string;
  /** Present when an HQL query selected whole entities — clickable records for the 2nd pane. */
  records?: SavedSearchRecordRef[];
}

export async function listSavedSearches(module?: string): Promise<SavedSearch[]> {
  const { data } = await http.get('/saved-searches', { params: clean({ module }) });
  return data;
}

export async function createSavedSearch(body: SavedSearch): Promise<SavedSearch> {
  const { data } = await http.post('/saved-searches', body);
  return data;
}

export async function updateSavedSearch(id: number, body: SavedSearch): Promise<SavedSearch> {
  const { data } = await http.put(`/saved-searches/${id}`, body);
  return data;
}

export async function deleteSavedSearch(id: number): Promise<void> {
  await http.delete(`/saved-searches/${id}`);
}

export async function runSavedSearch(id: number): Promise<SavedSearchResult> {
  const { data } = await http.post(`/saved-searches/${id}/run`);
  return data;
}

export async function runSavedSearchAdhoc(queryType: string, query: string): Promise<SavedSearchResult> {
  const { data } = await http.post('/saved-searches/run', { queryType, query });
  return data;
}

// ---- Repositories / folders / daily journal ---------------------------------

export interface RepoSettings {
  repo1: string;
  repo2: string;
  repo3: string;
  checkout: string;
  explorer: string;
}

export async function getRepoSettings(): Promise<RepoSettings> {
  const { data } = await http.get('/settings/repos');
  return data;
}

export async function updateRepoSettings(body: Partial<RepoSettings>): Promise<RepoSettings> {
  const { data } = await http.put('/settings/repos', body);
  return data;
}

/** Open a record's folder in repo 1/2/3 in the desktop file manager (backend launches it). */
export async function openRecordFolder(module: string, id: number, repo: 1 | 2 | 3): Promise<void> {
  await http.post(`/${module}/${id}/open`, null, { params: { repo } });
}

export interface DayJournal {
  module: string;
  id: number;
  created: boolean;
}

/** Get-or-create the day's summary ('smm') journal + its repo-1 symlinks. date = YYYY-MM-DD. */
export async function createJournalDay(date: string): Promise<DayJournal> {
  const { data } = await http.post('/calendar/journal-day', null, { params: { date } });
  return data;
}

// ---- Tags (relational, attached from the detail pane) -----------------------

export async function listTags(): Promise<TagRef[]> {
  const { data } = await http.get('/tags');
  return data;
}

/** Attach a tag to a record by id (existing) or name (created if new). Returns the updated record. */
export async function addTagToRecord(
  module: string,
  id: number,
  tag: { tagId?: number; name?: string },
): Promise<EntityRecord> {
  const { data } = await http.post(`/${module}/${id}/tags`, tag);
  return data;
}

export async function removeTagFromRecord(
  module: string,
  id: number,
  tagId: number,
): Promise<EntityRecord> {
  const { data } = await http.delete(`/${module}/${id}/tags/${tagId}`);
  return data;
}

// ---- knotes import ----------------------------------------------------------

export interface ImportNote {
  filename: string;
  parseable: boolean;
  timestamp?: string;
  typeCode?: string;
  moduleKey?: string;
  moduleLabel?: string;
  priority?: number;
  deptCode?: string;
  deptId?: number;
  deptName?: string;
  summary?: string;
  description?: string;
  body: string;
}

export interface ImportResult {
  module: string;
  id: number;
  displayTitle: string;
}

export interface ImportNotesList {
  dir: string;
  notes: ImportNote[];
}

export async function listImportNotes(): Promise<ImportNotesList> {
  const { data } = await http.get('/import/notes');
  return data;
}

/** Change the import folder (persisted server-side); returns the resolved path. */
export async function setImportDir(dir: string): Promise<string> {
  const { data } = await http.put('/import/notes/dir', { dir });
  return data.dir as string;
}

/** {@code resourceTypeId} applies to 'R' notes only — the type places the record's repo folder. */
export async function importNote(filename: string, resourceTypeId?: number): Promise<ImportResult> {
  const { data } = await http.post('/import/notes', { filename, resourceTypeId });
  return data;
}

// ---- full-text file round-trip ----------------------------------------------

export interface FieldDiff {
  field: string;
  oldValue?: string;
  newValue?: string;
}

export interface FulltextExport {
  path: string;
  content: string;
}

export async function exportFulltext(
  module: string,
  id: number,
  open?: boolean,
): Promise<FulltextExport> {
  const { data } = await http.post(`/${module}/${id}/fulltext/export`, null, {
    params: open === undefined ? undefined : { open },
  });
  return data;
}

export async function getFulltextChanges(module: string, id: number): Promise<FieldDiff[]> {
  const { data } = await http.get(`/${module}/${id}/fulltext/changes`);
  return data;
}

export async function applyFulltext(module: string, id: number): Promise<FieldDiff[]> {
  const { data } = await http.post(`/${module}/${id}/fulltext/apply`);
  return data;
}

/** Records whose exported _record.md has unsynced edits (across repo 1). */
export interface PendingSync {
  module: string;
  id: number;
  title: string;
  diffs: FieldDiff[];
}
export async function getPendingSync(): Promise<PendingSync[]> {
  const { data } = await http.get('/fulltext/pending');
  return data;
}

/** A checkout that already existed with different content — the old copy was kept aside. */
export interface TocConflict {
  module: string;
  id: number;
  title: string;
  backupFile: string;
}
/** Check out every bookmarked record + rebuild the master TOC (repo1/TOC.md). */
export interface BookmarkedTocResult {
  exported: number;
  tocPath: string;
  conflicts: TocConflict[];
}
export async function exportBookmarkedToc(): Promise<BookmarkedTocResult> {
  const { data } = await http.post('/fulltext/bookmarked-toc');
  return data;
}

// ---- image renamer ----------------------------------------------------------

export interface ImageList {
  dir: string;
  images: string[];
}

export async function listImages(dir: string): Promise<ImageList> {
  const { data } = await http.get('/images', { params: { dir } });
  return data;
}

export function imageFileUrl(dir: string, name: string): string {
  return `${BASE}api/images/file?dir=${encodeURIComponent(dir)}&name=${encodeURIComponent(name)}`;
}

export async function deleteImage(dir: string, name: string): Promise<string> {
  const { data } = await http.delete('/images', { params: { dir, name } });
  return data.trash as string; // path in the trash folder, for undo
}

export async function restoreImage(dir: string, name: string, trash: string): Promise<void> {
  await http.post('/images/restore', { dir, name, trash });
}

export async function renameImage(dir: string, from: string, to: string): Promise<string> {
  const { data } = await http.put('/images/rename', { dir, from, to });
  return data.name as string;
}

// ---- global full-text search ------------------------------------------------

export interface SearchHit {
  module: string;
  id: number;
  title: string;
  score: number;
  /** Excerpt of the lines matching the query terms. */
  snippet: string;
}

/**
 * Rows in the `search_doc` index. 0 means it was never built — the index fills incrementally as
 * records are written plus a bulk rebuild, so a fresh install pointed at an existing database has
 * an empty one and global search finds nothing while each module's own search still works.
 */
export async function getSearchIndexStatus(): Promise<{ indexed: number }> {
  const { data } = await http.get('/search/index-status');
  return data;
}

export async function searchAll(q: string, limit = 100): Promise<SearchHit[]> {
  const { data } = await http.get('/search', { params: { q, limit } });
  return data;
}

export interface DuplicatePair {
  module: string;
  id1: number;
  title1: string;
  id2: number;
  title2: string;
  score: number;
}

/** Candidate duplicate pairs already in the database, within one module — a one-off dedup scan. */
export async function findDuplicatePairs(module: string, limit = 50): Promise<DuplicatePair[]> {
  const { data } = await http.get('/search/duplicates', { params: { module, limit } });
  return data;
}

// ---- AI-assisted semantic dedup (local Ollama) --------------------------------

export interface OllamaStatus {
  reachable: boolean;
  hasEmbedModel: boolean;
  hasJudgeModel: boolean;
}

export async function getSemanticDedupStatus(): Promise<OllamaStatus> {
  const { data } = await http.get('/search/semantic-status');
  return data;
}

/** mode 'embedding' = fast, same-language only. mode 'llm' = slower, cross-language aware. */
export async function findSemanticDuplicates(
  module: string,
  mode: 'embedding' | 'llm',
  limit = 20,
): Promise<DuplicatePair[]> {
  const { data } = await http.get('/search/duplicates/semantic', { params: { module, mode, limit } });
  return data;
}

export async function reindexSearch(): Promise<number> {
  const { data } = await http.post('/search/reindex');
  return data.indexed as number;
}

// ---- task timer (server-side state for external probes) ---------------------

/** Report a tracking session start/stop to the backend — external probes (status bar) read it.
 * Best-effort: the localStorage flow in TaskTimeTracker stays the source of the journal stamps. */
export function reportTimerStart(taskId: number): void {
  http.post('/timer/start', { taskId }).catch(() => {});
}
export function reportTimerStop(): void {
  http.post('/timer/stop').catch(() => {});
}

// ---- kanban + admin dashboards ----------------------------------------------

/**
 * Kanban board data: bookmarked goals + the tasks shown with them (bookmarked or linked to a
 * bookmarked goal; open or recently completed). Full records so the board can render RecordCards.
 */
export interface KanbanData {
  goals: EntityRecord[];
  tasks: EntityRecord[];
}

export async function getKanban(): Promise<KanbanData> {
  const { data } = await http.get('/dashboard/kanban');
  return data;
}

export interface BookmarkItem {
  module: string;
  id: number;
  title: string;
}

export async function getBookmarks(): Promise<BookmarkItem[]> {
  const { data } = await http.get('/dashboard/bookmarks');
  return data;
}

export interface FieldStat {
  field: string;
  used: number;
  available: number;
}
export interface AdminModuleStats {
  module: string;
  total: number;
  parametric: FieldStat[];
}

export interface SyncedDepartment {
  id: number;
  code?: string;
  name?: string;
  priority?: number;
  orderNumber?: number;
}

export interface SyncedModule {
  moduleKey: string;
  label: string;
  code?: string;
  /** The Settings page's "Order" column — the clients follow the same order the sidebar does. */
  sortOrder: number;
  types: Lookup[];
}

/** What the mobile/terminal/desktop clients are expected to be filtering to — for spotting drift. */
export interface ClientSyncView {
  departments: SyncedDepartment[];
  modules: SyncedModule[];
}

export async function getClientSyncView(): Promise<ClientSyncView> {
  const { data } = await http.get('/admin/client-sync');
  return data;
}

export async function getAdminStats(): Promise<AdminModuleStats[]> {
  const { data } = await http.get('/admin/stats');
  return data;
}

// ---- batch edit -------------------------------------------------------------

export interface BatchResult {
  updated: number;
  fields: string[];
}

export async function batchEdit(
  module: string,
  ids: number[],
  command: string,
): Promise<BatchResult> {
  const { data } = await http.post(`/${module}/batch`, { ids, command });
  return data;
}

// ---- merge two records --------------------------------------------------------

export async function mergeRecords(
  module: string,
  primaryId: number,
  secondaryId: number,
): Promise<EntityRecord> {
  const { data } = await http.post(`/${module}/merge`, { primaryId, secondaryId });
  return data;
}

// ---- trash (soft-deleted records) ----------------------------------------------

export interface TrashEntry {
  module: string;
  id: number;
  title: string;
  deletedOn: string;
  mergedIntoId?: number | null;
  mergedIntoTitle?: string | null;
}

export async function listTrash(): Promise<TrashEntry[]> {
  const { data } = await http.get('/trash');
  return data;
}

export async function restoreFromTrash(module: string, id: number): Promise<void> {
  await http.post(`/trash/${module}/${id}/restore`);
}

// ---- record cover -----------------------------------------------------------

export function coverUrl(module: string, id: number): string {
  return `${BASE}api/attachments/${module}/${id}/cover`;
}

// ---- authenticated media (covers/files the browser can't send the token for) ------------------

/**
 * Fetch a same-origin `/api/...` URL as a Blob, carrying the JWT via the axios bearer interceptor.
 * Needed because `<img src>` / `<a href>` requests the browser makes on its own don't include the
 * Authorization header, so they 401 under the security layer.
 */
export async function fetchBlob(url: string): Promise<Blob> {
  // The callers build these context-absolute (`${BASE}api/...`) so the same string also works as a
  // plain href. axios would prepend its own baseURL again, so strip the prefix that's actually
  // there: under a servlet context path BASE is e.g. "/nibras/", so the URL starts with
  // "/nibras/api" and a hardcoded /^\/api/ never matches — that produced a doubled
  // "/nibras/api/nibras/api/..." and every cover/download silently failed off the root context.
  const prefix = `${BASE}api`;
  const path = url.startsWith(prefix) ? url.slice(prefix.length) : url.replace(/^\/api/, '');
  const { data } = await http.get(path, { responseType: 'blob' });
  return data as Blob;
}

/** Authenticated download: fetch the file with the token, then save it as {@code filename}. */
export async function downloadFile(url: string, filename: string): Promise<void> {
  const objectUrl = URL.createObjectURL(await fetchBlob(url));
  const a = document.createElement('a');
  a.href = objectUrl;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(objectUrl), 10_000);
}

// ---- server info (status bar) -----------------------------------------------

export interface ServerInfo {
  app: string;
  version: string;
  codename?: string;
  serverIp: string;
  serverAddress: string;
  port: number;
  dbName: string;
  repoUrl?: string;
}

export async function getInfo(): Promise<ServerInfo> {
  const { data } = await http.get('/info');
  return data;
}

export interface UpdateInfo {
  configured: boolean;
  current: string;
  latest?: string;
  updateAvailable: boolean;
  releaseUrl?: string;
  error?: string;
}

/** Check GitHub for a newer release (requires nibras.repo to be configured on the server). */
export async function checkUpdates(): Promise<UpdateInfo> {
  const { data } = await http.get('/info/updates');
  return data;
}

// ---- static-site generator --------------------------------------------------

export interface PublishableSearch {
  id: number;
  summary: string;
  module?: string;
  entity?: string;
  queryType: string;
}

export interface SitesInfo {
  outputDir: string;
  searches: PublishableSearch[];
}

export interface SiteResult {
  outputDir: string;
  indexPath: string;
  posts: number;
  mediaFiles: number;
  warnings: string[];
}

export async function getSites(): Promise<SitesInfo> {
  const { data } = await http.get('/sites');
  return data;
}

export async function generateSite(savedSearchId: number, out?: string): Promise<SiteResult> {
  const { data } = await http.post(`/sites/${savedSearchId}/generate`, { out });
  return data;
}

// ---- dashboard KPIs ---------------------------------------------------------

export interface Kpi {
  key: string;
  label: string;
  value: number;
  hint?: string;
}

export async function getKpis(): Promise<Kpi[]> {
  const { data } = await http.get('/dashboard/kpis');
  return data;
}

// ---- insights (the fixed Insights page — activity histograms + review nudges) -----------

export interface InsightsDayCount {
  date: string;
  count: number;
}

export interface ModuleInsight {
  key: string;
  label: string;
  total: number;
  createdRecent: number;
  updatedRecent: number;
  percentUpdatedRecent: number;
  createdSeries: InsightsDayCount[];
  updatedSeries: InsightsDayCount[];
}

export interface InsightsSummary {
  from: string;
  to: string;
  reviewDays: number;
  modules: ModuleInsight[];
}

/** `from`/`to` default to a 90-day window ending today when omitted; `reviewDays` (the trailing
 * "reviewed recently" window) defaults to 7. */
export async function getInsights(from?: string, to?: string, reviewDays?: number): Promise<InsightsSummary> {
  const { data } = await http.get('/insights/summary', { params: clean({ from, to, reviewDays }) });
  return data;
}

// ---- indicators -------------------------------------------------------------

export interface DueIndicator {
  id: number;
  name: string;
  frequency?: string;
  kind: 'QUERY' | 'FILE' | 'MANUAL';
  proposed?: number;
  proposeError?: string;
  lastValue?: number;
  lastDate?: string;
  scale?: string;
}

export interface SeriesPoint {
  date: string;
  value: number;
  notes?: string;
}

export async function getIndicatorsDue(): Promise<DueIndicator[]> {
  const { data } = await http.get('/indicators/due');
  return data;
}

export async function proposeIndicator(id: number): Promise<{ value?: number; error?: string }> {
  const { data } = await http.get(`/indicators/${id}/propose`);
  return data;
}

export async function saveIndicatorData(
  id: number,
  value: number,
  date?: string,
  notes?: string,
): Promise<void> {
  await http.post(`/indicators/${id}/data`, { value, date, notes });
}

export async function getIndicatorSeries(id: number): Promise<SeriesPoint[]> {
  const { data } = await http.get(`/indicators/${id}/series`);
  return data;
}

export async function listMonitoredIndicators(): Promise<EntityRecord[]> {
  const { data } = await http.get('/indicators', { params: { bookmarked: true, size: 500, sort: 'name' } });
  return data.content;
}

// ---- Custom dashboards (the dashboard builder) --------------------------------

/** A region's `config` is a JSON string whose shape depends on componentType — see
 * frontend/src/components/DashboardComponents.tsx for what each type reads/writes. */
export interface CustomDashboardRegion {
  id?: number;
  orderIndex: number;
  componentType: string;
  config?: string;
  /** How many of the dashboard's grid columns this region spans. Default 1. */
  colSpan?: number;
}

export interface CustomDashboard {
  id?: number;
  name: string;
  columnsCount: number;
  /** Minutes between auto full-page-reloads of the chrome-free view. Null/0 disables it. */
  refreshMinutes?: number | null;
  regions: CustomDashboardRegion[];
}

export async function listCustomDashboards(): Promise<CustomDashboard[]> {
  const { data } = await http.get('/custom-dashboards');
  return data;
}

export async function getCustomDashboard(id: number): Promise<CustomDashboard> {
  const { data } = await http.get(`/custom-dashboards/${id}`);
  return data;
}

export async function createCustomDashboard(body: CustomDashboard): Promise<CustomDashboard> {
  const { data } = await http.post('/custom-dashboards', body);
  return data;
}

export async function updateCustomDashboard(id: number, body: CustomDashboard): Promise<CustomDashboard> {
  const { data } = await http.put(`/custom-dashboards/${id}`, body);
  return data;
}

export async function deleteCustomDashboard(id: number): Promise<void> {
  await http.delete(`/custom-dashboards/${id}`);
}

export interface DashboardFileContent {
  path: string;
  content: string;
  truncated: boolean;
}

/** Reads a file's plain-text content under repo 1/2/3, for the "file" component. */
export async function getDashboardFileContent(repo: number, path: string): Promise<DashboardFileContent> {
  const { data } = await http.get('/custom-dashboards/file-content', { params: { repo, path } });
  return data;
}

// ---- Public (unauthenticated) dashboard snapshot — the chrome-free /view page --------

export interface PublicIndicatorWithSeries {
  indicator: EntityRecord;
  series: SeriesPoint[];
}

export interface PublicGoalHeatmap {
  goalId: number;
  goalCode?: string;
  indicators: PublicIndicatorWithSeries[];
}

export interface PublicDashboardRegion {
  id?: number;
  orderIndex: number;
  componentType: string;
  colSpan?: number;
  config?: string;
  quote: EntityRecord | null;
  calendarEvents: CalendarEvent[] | null;
  goalsHeatmap: PublicGoalHeatmap[] | null;
  kanbanBoard: KanbanData | null;
  file: DashboardFileContent | null;
}

export interface PublicDashboardSnapshot {
  id: number;
  name: string;
  columnsCount: number;
  refreshMinutes?: number | null;
  regions: PublicDashboardRegion[];
}

/** Every region's data pre-resolved server-side in one call — no auth, no per-component queries.
 * Backs the chrome-free dashboard view meant for a TV/kiosk with no session at all. */
export async function getPublicDashboard(id: number): Promise<PublicDashboardSnapshot> {
  const { data } = await http.get(`/public/custom-dashboards/${id}`);
  return data;
}

// ---- Sprints ---------------------------------------------------------------

/** One task's membership of one sprint (also used for the candidate list, where `id` is null). */
export interface SprintItem {
  id: number | null;
  taskId: number;
  title: string;
  completed: boolean;
  completedOn?: string;
  /** Snapshot of the estimate at commit time, in minutes. */
  plannedMinutes: number;
  /** True when plannedMinutes is the fallback, not the task's own estimate. */
  estimateAssumed: boolean;
  /** Times this task was committed to a CLOSED sprint and not finished, excluding this one. */
  previousMisses: number;
  sortOrder?: number;
}

export interface Sprint {
  id: number;
  summary?: string;
  notes?: string;
  startDate?: string;
  endDate?: string;
  /** Absent while the sprint is open — the API omits nulls. */
  closedOn?: string;
  items: SprintItem[];
  plannedMinutes: number;
  /** Minutes between start and end, or absent when either is unset. */
  windowMinutes?: number;
  /** Titles of the tasks counted at the fallback because they carry no estimate. */
  unestimated: string[];
  completedCount: number;
}

export async function listSprints(): Promise<Sprint[]> {
  const { data } = await http.get('/sprints');
  return data;
}

export async function getSprint(id: number): Promise<Sprint> {
  const { data } = await http.get(`/sprints/${id}`);
  return data;
}

/** Bookmarked, unfinished tasks not already in `sprintId`, each with its miss count. */
export async function sprintCandidates(sprintId?: number): Promise<SprintItem[]> {
  const { data } = await http.get('/sprints/candidates', { params: clean({ sprintId }) });
  return data;
}

export async function sprintDefaultMinutes(): Promise<number> {
  const { data } = await http.get('/sprints/default-minutes');
  return data;
}

export async function createSprint(body: {
  summary?: string;
  startDate?: string;
  endDate?: string;
  taskIds?: number[];
}): Promise<Sprint> {
  const { data } = await http.post('/sprints', body);
  return data;
}

export async function updateSprint(
  id: number,
  body: { summary?: string; notes?: string; startDate?: string; endDate?: string },
): Promise<Sprint> {
  const { data } = await http.put(`/sprints/${id}`, body);
  return data;
}

export async function deleteSprint(id: number): Promise<void> {
  await http.delete(`/sprints/${id}`);
}

export async function commitToSprint(id: number, taskIds: number[]): Promise<Sprint> {
  const { data } = await http.post(`/sprints/${id}/tasks`, { taskIds });
  return data;
}

export async function uncommitFromSprint(id: number, taskId: number): Promise<Sprint> {
  const { data } = await http.delete(`/sprints/${id}/tasks/${taskId}`);
  return data;
}

/** The review: tick a task done (or undo). Also completes the task itself, server-side. */
export async function setSprintTaskCompleted(
  id: number,
  taskId: number,
  completed: boolean,
): Promise<Sprint> {
  const { data } = await http.put(`/sprints/${id}/tasks/${taskId}`, { completed });
  return data;
}

/** Close the review (or re-open it). Only a closed sprint's misses count against a task. */
export async function setSprintClosed(id: number, closed: boolean): Promise<Sprint> {
  const { data } = await http.put(`/sprints/${id}/closed`, { completed: closed });
  return data;
}
