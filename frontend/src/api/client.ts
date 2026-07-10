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
  tags?: TagRef[];
  hasCover?: boolean;
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
  bookmarked?: boolean;
  statusId?: number;
  typeId?: number;
  tagId?: number;
  page?: number;
  size?: number;
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
  'overdue', 'dueSoon', 'highPriority', 'recentCreated', 'recentUpdated', 'bookmarked',
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

export interface CalendarEvent {
  module: 'journals' | 'planners';
  id: number;
  title: string;
  start: string;
  end?: string;
}

export async function getCalendarEvents(
  from: string,
  to: string,
  modules = 'journals,planners',
): Promise<CalendarEvent[]> {
  const { data } = await http.get('/calendar/events', { params: { from, to, modules } });
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

export async function importNote(filename: string): Promise<ImportResult> {
  const { data } = await http.post('/import/notes', { filename });
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

export async function searchAll(q: string, limit = 100): Promise<SearchHit[]> {
  const { data } = await http.get('/search', { params: { q, limit } });
  return data;
}

export async function reindexSearch(): Promise<number> {
  const { data } = await http.post('/search/reindex');
  return data.indexed as number;
}

// ---- kanban + admin dashboards ----------------------------------------------

export interface KanbanCard {
  module: 'goals' | 'tasks';
  id: number;
  title: string;
  priority?: number;
  departmentId?: number;
  departmentCode?: string;
  departmentName?: string;
  groupId?: number;
  groupName?: string;
}

export async function getKanban(): Promise<KanbanCard[]> {
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
  const { data } = await http.get(url.replace(/^\/api/, ''), { responseType: 'blob' });
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
