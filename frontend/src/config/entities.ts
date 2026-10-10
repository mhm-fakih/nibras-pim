export type FieldType =
  | 'text'
  | 'url'
  | 'textarea'
  | 'richtext'
  | 'markdown'
  | 'number'
  | 'switch'
  | 'datetime'
  | 'status'
  | 'type'
  | 'department'
  | 'course'
  | 'book'
  | 'goal'
  | 'tags'
  /** People a record is about — the same multi-select as `tags`, over contacts. */
  | 'contacts'
  | 'recurrence';

export interface FieldDef {
  name: string;
  label: string;
  type: FieldType;
  /**
   * `recurrence` only. The two modules mean different things by repeating, so the picker adapts:
   *
   * - `'chain'` (tasks) — one occurrence exists at a time and the next is spawned when this one is
   *   completed, or by date if "fixed schedule" is on. That switch is offered.
   * - `'series'` (planners) — the record holds a rule that the calendar expands into occurrences.
   *   Always by date, so the switch is meaningless and hidden; an "ends" control is offered
   *   instead, which a chain has no use for since it only ever has one future instance.
   */
  recurrenceMode?: 'chain' | 'series';
  /**
   * Settings → Features key that must be on for this field to be drawn (see TOGGLEABLE_FEATURES).
   * Omitted on all but a handful of fields — the default is that a field a module defines is a
   * field you see.
   *
   * Affects DISPLAY only. A hidden field is still loaded into the form and still written back
   * unchanged on save: the payload builder works from the full field list precisely so that
   * turning a field off never quietly empties it. See EntityForm.
   */
  feature?: string;
}

export interface EntityConfig {
  /** Route + API slug. */
  key: string;
  label: string;
  icon: string;
  /** Whether this entity exposes status / type filters. */
  hasStatus: boolean;
  hasType: boolean;
  fields: FieldDef[];
}

const common: FieldDef[] = [
  { name: 'department', label: 'Department', type: 'department' },
  { name: 'course', label: 'Course', type: 'course' },
  { name: 'priority', label: 'Priority', type: 'number' },
  { name: 'bookmarked', label: 'Bookmarked', type: 'switch' },
  { name: 'tags', label: 'Tags', type: 'tags' },
];

export const ENTITIES: EntityConfig[] = [
  {
    key: 'tasks',
    label: 'Tasks',
    icon: 'CheckCircleOutline',
    hasStatus: true,
    hasType: false,
    fields: [
      { name: 'summary', label: 'Summary', type: 'text' },
      { name: 'description', label: 'Description', type: 'richtext' },
      { name: 'mainHighlights', label: 'Highlights', type: 'textarea' },
      { name: 'notes', label: 'Notes', type: 'textarea' },
      { name: 'status', label: 'Status', type: 'status' },
      { name: 'goal', label: 'Goal', type: 'goal' },
      { name: 'percentCompleted', label: '% complete', type: 'number' },
      // Minutes, whole numbers — 10, 20, 30, 45, 60. Feeds a sprint's planned total; without it
      // the sprint counts the task at its configured default and says which tasks those were. The
      // unit is the point: a task you can't estimate in minutes is too big to commit to a sprint.
      { name: 'plannedDuration', label: 'Planned duration (min)', type: 'number' },
      { name: 'startDate', label: 'Start', type: 'datetime' },
      { name: 'endDate', label: 'End', type: 'datetime' },
      { name: 'urgent', label: 'Urgent', type: 'switch' },
      { name: 'orderNumber', label: 'Order #', type: 'number' },
      // Pseudo-field: renders a compound recurrence picker over recurring, recurrencePreset,
      // recurrenceInterval, recurrenceCron, fixedSchedule — see RecurrenceField in EntityForm.tsx.
      { name: 'recurrence', label: 'Repeats', type: 'recurrence', recurrenceMode: 'chain' },
      // Tagging by PERSON as well as by tag. Backed by the legacy `task_contact` join, and read
      // back by the phone's Contacts page, which groups open work under the people it is with.
      { name: 'contacts', label: 'People', type: 'contacts' },
      ...common,
    ],
  },
  {
    key: 'journals',
    label: 'Journal',
    icon: 'MenuBook',
    hasStatus: false,
    hasType: true,
    fields: [
      { name: 'summary', label: 'Summary', type: 'text' },
      { name: 'description', label: 'Description', type: 'richtext' },
      { name: 'mainHighlights', label: 'Highlights', type: 'textarea' },
      { name: 'notes', label: 'Notes', type: 'textarea' },
      { name: 'type', label: 'Type', type: 'type' },
      { name: 'startDate', label: 'Start', type: 'datetime' },
      { name: 'endDate', label: 'End', type: 'datetime' },
      { name: 'energyLevel', label: 'Energy', type: 'number' },
      ...common,
    ],
  },
  {
    key: 'planners',
    label: 'Planner',
    icon: 'EventNote',
    hasStatus: true,
    hasType: true,
    fields: [
      { name: 'summary', label: 'Summary', type: 'text' },
      { name: 'description', label: 'Plan', type: 'richtext' },
      { name: 'mainHighlights', label: 'Highlights', type: 'textarea' },
      { name: 'reality', label: 'Reality', type: 'textarea' },
      { name: 'notes', label: 'Notes', type: 'textarea' },
      { name: 'type', label: 'Type', type: 'type' },
      { name: 'status', label: 'Status', type: 'status' },
      { name: 'startDate', label: 'Start', type: 'datetime' },
      { name: 'endDate', label: 'End', type: 'datetime' },
      { name: 'percentCompleted', label: '% complete', type: 'number' },
      // A recurring planner is a SERIES: this record holds the rule and its own start/end are the
      // first occurrence; the calendar expands the rest on the fly and only writes a row when one
      // is touched. See PlannerSeriesService on the backend.
      { name: 'recurrence', label: 'Repeats', type: 'recurrence', recurrenceMode: 'series' },
      ...common,
    ],
  },
  {
    key: 'resources',
    label: 'Resources',
    icon: 'LocalLibrary',
    hasStatus: true,
    hasType: true,
    fields: [
      { name: 'type', label: 'Type', type: 'type' },
      { name: 'title', label: 'Title', type: 'text' },
      { name: 'author', label: 'Author', type: 'text' },
      { name: 'publisher', label: 'Publisher', type: 'text' },
      { name: 'edition', label: 'Edition', type: 'text' },
      { name: 'isbn', label: 'ISBN', type: 'text' },
      { name: 'shortDescription', label: 'Short description', type: 'textarea' },
      { name: 'description', label: 'Description', type: 'richtext' },
      // The long tail of a resource: the text itself, what you pulled out of it, what you thought
      // of it, where it came from, and when you finished it. All real fields, but between them
      // they make the form several screens long for someone who only ever fills in title/author —
      // hence the single switch (Settings → Features → "Resource detail fields").
      { name: 'fullText', label: 'Full text', type: 'markdown', feature: 'resourceExtras' },
      { name: 'highlights', label: 'Highlights', type: 'markdown', feature: 'resourceExtras' },
      { name: 'comments', label: 'Comments', type: 'textarea', feature: 'resourceExtras' },
      { name: 'notes', label: 'Notes', type: 'textarea' },
      { name: 'url', label: 'URL', type: 'url', feature: 'resourceExtras' },
      { name: 'status', label: 'Status', type: 'status' },
      { name: 'resourceYear', label: 'Year', type: 'text' },
      { name: 'pages', label: 'Pages', type: 'text' },
      { name: 'isRead', label: 'Read', type: 'switch' },
      { name: 'readOn', label: 'Read on', type: 'datetime', feature: 'resourceExtras' },
      { name: 'percentCompleted', label: '% complete', type: 'number' },
      ...common,
    ],
  },
  {
    key: 'notes',
    label: 'Notes',
    icon: 'StickyNote2',
    hasStatus: true,
    hasType: true,
    fields: [
      { name: 'summary', label: 'Summary', type: 'text' },
      { name: 'shortDescription', label: 'Short description', type: 'textarea' },
      { name: 'description', label: 'Content', type: 'richtext' },
      { name: 'reaction', label: 'Reaction', type: 'textarea' },
      { name: 'mainHighlights', label: 'Highlights', type: 'textarea' },
      { name: 'notes', label: 'Notes', type: 'textarea' },
      { name: 'type', label: 'Type', type: 'type' },
      { name: 'status', label: 'Status', type: 'status' },
      // The resource the note is about. The column (index_card.book_id) has been populated all
      // along — 1063 of 13315 cards carry one — but was never mapped, so nothing could show it.
      { name: 'book', label: 'Resource', type: 'book' },
      { name: 'pages', label: 'Pages', type: 'text' },
      ...common,
    ],
  },
  {
    key: 'goals',
    label: 'Goals',
    icon: 'Flag',
    hasStatus: true,
    hasType: true,
    fields: [
      // Goals also have a legacy `title` column, but like the other main records the form
      // captures only `summary` (title stays DB-mapped for back-compat; see Goal.feedTitle).
      { name: 'summary', label: 'Summary', type: 'text' },
      { name: 'description', label: 'Description', type: 'richtext' },
      { name: 'mainHighlights', label: 'Highlights', type: 'textarea' },
      { name: 'notes', label: 'Notes', type: 'textarea' },
      { name: 'code', label: 'Code', type: 'text' },
      { name: 'status', label: 'Status', type: 'status' },
      { name: 'type', label: 'Type', type: 'type' },
      { name: 'percentCompleted', label: '% complete', type: 'number' },
      { name: 'startDate', label: 'Start', type: 'datetime' },
      { name: 'endDate', label: 'End', type: 'datetime' },
      { name: 'completedOn', label: 'Completed', type: 'datetime' },
      { name: 'dashboard', label: 'Show habit heatmap on dashboards', type: 'switch' },
      ...common,
    ],
  },
  {
    key: 'writings',
    label: 'Writings',
    icon: 'Create',
    hasStatus: true,
    hasType: true,
    fields: [
      { name: 'summary', label: 'Summary', type: 'text' },
      { name: 'description', label: 'Body', type: 'richtext' },
      { name: 'mainHighlights', label: 'Highlights', type: 'textarea' },
      { name: 'shortDescription', label: 'Short description', type: 'textarea' },
      { name: 'notes', label: 'Notes', type: 'textarea' },
      { name: 'slug', label: 'Slug', type: 'text' },
      { name: 'source', label: 'Source', type: 'text' },
      { name: 'status', label: 'Status', type: 'status' },
      { name: 'type', label: 'Type', type: 'type' },
      { name: 'bornOn', label: 'Born on', type: 'datetime' },
      ...common,
    ],
  },
];

// Contacts: people, with the same list/detail/edit as any other module.
//
// Its own module rather than a lookup table, because a contact is a record you keep — notes on a
// person, their number, what you owe them — and because BOOKMARKED has to be settable. The phone's
// Contacts page selects on that flag, and before this module nothing in any client could change it:
// the bookmarked people were bookmarked in the legacy system and the rest were unreachable.
//
// No status/type/department/course: the legacy `contact` table has no such columns, so the common
// block is not spread in here. `summary` is NOT NULL there and the server fills it from the name
// when a contact is created with only one.
ENTITIES.push({
  key: 'contacts',
  label: 'Contacts',
  icon: 'Groups',
  hasStatus: false,
  hasType: false,
  fields: [
    // `summary` IS the person's name in this table — it is set on all 187 rows and `name` is empty
    // on every one of them. So it leads, and carries the label a reader expects.
    { name: 'summary', label: 'Name', type: 'text' },
    { name: 'code', label: 'Code', type: 'text' },
    { name: 'jobTitle', label: 'Job title', type: 'text' },
    { name: 'telephones', label: 'Telephones', type: 'text' },
    { name: 'email', label: 'Email', type: 'text' },
    { name: 'address', label: 'Address', type: 'text' },
    { name: 'description', label: 'About', type: 'richtext' },
    { name: 'notes', label: 'Notes', type: 'textarea' },
    // Kept, and last: empty throughout today, but it is a real column and editing a record must
    // not silently drop what is in it.
    { name: 'name', label: 'Alternate name', type: 'text' },
    { name: 'priority', label: 'Priority', type: 'number' },
    { name: 'bookmarked', label: 'Bookmarked', type: 'switch' },
  ],
});

// Department & Course are first-class modules that also categorize the records above.
ENTITIES.push(
  {
    key: 'departments',
    label: 'Departments',
    icon: 'AccountTree',
    hasStatus: false,
    hasType: false,
    fields: [
      { name: 'name', label: 'Name', type: 'text' },
      { name: 'code', label: 'Code', type: 'text' },
      { name: 'summary', label: 'Summary', type: 'text' },
      { name: 'notes', label: 'Notes', type: 'textarea' },
      { name: 'priority', label: 'Priority', type: 'number' },
      { name: 'bookmarked', label: 'Bookmarked', type: 'switch' },
    ],
  },
  {
    key: 'courses',
    label: 'Courses',
    icon: 'School',
    hasStatus: false,
    hasType: false,
    fields: [
      { name: 'summary', label: 'Title', type: 'text' },
      { name: 'code', label: 'Code', type: 'text' },
      { name: 'department', label: 'Department', type: 'department' },
      { name: 'notes', label: 'Notes', type: 'textarea' },
      // The knotes vocabulary that points at this course. Canonical tag first, then aliases:
      // `A.salat, salat, صلاة`. This is what resolves a note's #A.salat to this record on the
      // Local notes page, which is why it is editable here rather than in a file.
      { name: 'tags', label: 'Note tags — canonical first, then aliases', type: 'text' },
      { name: 'priority', label: 'Priority', type: 'number' },
      { name: 'bookmarked', label: 'Bookmarked', type: 'switch' },
    ],
  },
  {
    key: 'excerpts',
    label: 'Excerpts',
    icon: 'MenuBook',
    hasStatus: false,
    hasType: false,
    fields: [
      { name: 'summary', label: 'Summary', type: 'text' },
      { name: 'book', label: 'Book', type: 'book' },
      { name: 'chapters', label: 'Chapter(s)', type: 'text' },
      { name: 'chapter', label: 'Chapter #', type: 'number' },
      { name: 'title', label: 'Title', type: 'text' },
      { name: 'description', label: 'Content', type: 'richtext' },
      { name: 'notes', label: 'Notes', type: 'textarea' },
      { name: 'priority', label: 'Priority', type: 'number' },
      { name: 'bookmarked', label: 'Bookmarked', type: 'switch' },
    ],
  },
);

export const ENTITY_BY_KEY: Record<string, EntityConfig> = Object.fromEntries(
  ENTITIES.map((e) => [e.key, e]),
);

/** Modules the backend merge endpoint supports (mirrors MergeService.CLASSES on the backend). */
export const MERGEABLE_MODULES = new Set([
  'tasks', 'journals', 'planners', 'resources', 'notes', 'goals', 'writings',
]);

/**
 * Indicator definitions are managed from the Indicators dashboard (not the generic module list),
 * so this config lives outside ENTITIES — it only drives the New/Edit form there.
 */
export const INDICATOR_CONFIG: EntityConfig = {
  key: 'indicators',
  label: 'Indicator',
  icon: 'ShowChart',
  hasStatus: false,
  hasType: false,
  fields: [
    { name: 'name', label: 'Name', type: 'text' },
    { name: 'code', label: 'Code', type: 'text' },
    { name: 'description', label: 'Description', type: 'textarea' },
    { name: 'frequency', label: 'Frequency (daily / weekly)', type: 'text' },
    { name: 'scale', label: 'Scale / unit', type: 'text' },
    { name: 'query', label: 'Query (SQL/HQL — for query indicators)', type: 'textarea' },
    { name: 'path', label: 'Folder path (for file-count indicators)', type: 'text' },
    { name: 'extensions', label: 'Extensions (file-count filter)', type: 'text' },
    { name: 'script', label: 'Script (shell — prints a number, e.g. du -sm /path | cut -f1)', type: 'textarea' },
    { name: 'category', label: 'Category (code — same category overlays on one chart, e.g. Ta)', type: 'text' },
    { name: 'color', label: 'Colour (chart line, e.g. #1f77b4 — auto-filled if blank)', type: 'text' },
    { name: 'priority', label: 'Priority', type: 'number' },
    { name: 'bookmarked', label: 'Actively monitored', type: 'switch' },
    {
      name: 'autoCollect',
      label: 'Auto-collect (query/file-count/script, end of day — see Settings)',
      type: 'switch',
    },
    { name: 'goal', label: 'Goal (habit tracking — daily indicator linked to a goal)', type: 'goal' },
    { name: 'summary', label: 'Summary', type: 'text' },
    { name: 'notes', label: 'Notes', type: 'textarea' },
    { name: 'startDate', label: 'Start', type: 'datetime' },
    { name: 'endDate', label: 'End', type: 'datetime' },
  ],
};

/** Slug → JPA class name (the `entity` value stored in the audit trail). */
export const ENTITY_CLASS_BY_KEY: Record<string, string> = {
  tasks: 'Task',
  journals: 'Journal',
  planners: 'Planner',
  resources: 'Book',
  notes: 'IndexCard',
  goals: 'Goal',
  writings: 'Writing',
  departments: 'Department',
  courses: 'Course',
  excerpts: 'Excerpt',
};
