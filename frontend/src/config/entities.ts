export type FieldType =
  | 'text'
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
  | 'tags';

export interface FieldDef {
  name: string;
  label: string;
  type: FieldType;
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
  { name: 'isPrivate', label: 'Private', type: 'switch' },
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
      { name: 'notes', label: 'Notes', type: 'textarea' },
      { name: 'status', label: 'Status', type: 'status' },
      { name: 'goal', label: 'Goal', type: 'goal' },
      { name: 'percentCompleted', label: '% complete', type: 'number' },
      { name: 'startDate', label: 'Start', type: 'datetime' },
      { name: 'endDate', label: 'End', type: 'datetime' },
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
      { name: 'description', label: 'Entry', type: 'richtext' },
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
      { name: 'reality', label: 'Reality', type: 'textarea' },
      { name: 'notes', label: 'Notes', type: 'textarea' },
      { name: 'type', label: 'Type', type: 'type' },
      { name: 'status', label: 'Status', type: 'status' },
      { name: 'startDate', label: 'Start', type: 'datetime' },
      { name: 'endDate', label: 'End', type: 'datetime' },
      { name: 'percentCompleted', label: '% complete', type: 'number' },
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
      { name: 'fullText', label: 'Full text', type: 'markdown' },
      { name: 'highlights', label: 'Highlights', type: 'markdown' },
      { name: 'comments', label: 'Comments', type: 'textarea' },
      { name: 'notes', label: 'Notes', type: 'textarea' },
      { name: 'url', label: 'URL', type: 'text' },
      { name: 'status', label: 'Status', type: 'status' },
      { name: 'resourceYear', label: 'Year', type: 'text' },
      { name: 'pages', label: 'Pages', type: 'text' },
      { name: 'isRead', label: 'Read', type: 'switch' },
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
      { name: 'notes', label: 'Notes', type: 'textarea' },
      { name: 'code', label: 'Code', type: 'text' },
      { name: 'status', label: 'Status', type: 'status' },
      { name: 'type', label: 'Type', type: 'type' },
      { name: 'percentCompleted', label: '% complete', type: 'number' },
      { name: 'startDate', label: 'Start', type: 'datetime' },
      { name: 'endDate', label: 'End', type: 'datetime' },
      { name: 'completedOn', label: 'Completed', type: 'datetime' },
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
