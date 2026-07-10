// Keyboard-shortcut catalog. Drives the Shift+? help overlay and the `g`-chord navigation so the app
// can be driven mouselessly.

export interface ShortcutItem {
  keys: string;
  desc: string;
}
export interface ShortcutGroup {
  title: string;
  items: ShortcutItem[];
}

/** `g` then <key> -> route. Shown in the help under "Go to". */
export const GO_TO: { key: string; path: string; label: string }[] = [
  { key: 't', path: '/tasks', label: 'Tasks' },
  { key: 'g', path: '/goals', label: 'Goals' },
  { key: 'n', path: '/notes', label: 'Notes' },
  { key: 'j', path: '/journals', label: 'Journal' },
  { key: 'p', path: '/planners', label: 'Planner' },
  { key: 'r', path: '/resources', label: 'Resources' },
  { key: 'w', path: '/writings', label: 'Writings' },
  { key: 'c', path: '/calendar', label: 'Calendar' },
  { key: 'k', path: '/kanban', label: 'Kanban' },
  { key: 'b', path: '/bookmarks', label: 'Bookmarks' },
  { key: 'i', path: '/indicators', label: 'Indicators' },
  { key: 'a', path: '/admin', label: 'Admin' },
  { key: 'm', path: '/import', label: 'Import notes' },
  { key: 's', path: '/settings', label: 'Settings' },
];

export const SHORTCUTS: ShortcutGroup[] = [
  {
    title: 'Global',
    items: [
      { keys: '?', desc: 'Show / hide this shortcuts help' },
      { keys: '/', desc: 'Focus the global search' },
      { keys: 'g then …', desc: 'Go to a module/tool (see "Go to" below)' },
      { keys: 'Esc', desc: 'Close dialog / form, or go back to the list' },
    ],
  },
  {
    title: 'List (middle pane)',
    items: [
      { keys: 'j', desc: 'Select next record' },
      { keys: 'k', desc: 'Select previous record' },
      { keys: 'o', desc: 'Open the selected record' },
      { keys: 'n', desc: 'New record' },
    ],
  },
  {
    title: 'Record (detail pane)',
    items: [
      { keys: 'e', desc: 'Edit the selected record' },
      { keys: 'f then 1 / 2 / 3', desc: 'Open the record folder in repository 1 / 2 / 3' },
    ],
  },
  {
    title: 'Quick-edit (with a record selected)',
    items: [
      { keys: 'p', desc: 'Set priority (type a number, Enter)' },
      { keys: '#', desc: 'Set type (pick a code)' },
      { keys: '?', desc: 'Set status (pick a code)' },
      { keys: '@', desc: 'Set context (pick a code)' },
      { keys: 'd', desc: 'Set department (pick a code)' },
      { keys: 'c', desc: 'Set course (pick a code)' },
      { keys: '*', desc: 'Toggle bookmark' },
    ],
  },
  {
    title: 'Calendar',
    items: [{ keys: 'click a day', desc: "Get/create that day's summary journal (smm) and open it" }],
  },
];
