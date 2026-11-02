-- Starter data for a FRESH install, run once after schema.sql (see FreshInstallMigrationStrategy).
-- Only what the app needs to be usable on day one: the modules, the permission matrix, and short,
-- plain lists for every parameter a record is classified by. No personal records. Everything here
-- can be renamed, added to or removed later on the Parameters page; the setup wizard shown on the
-- first sign-in picks the modules and folders.

-- Modules: all present, all on. The setup wizard turns off the ones not wanted.
INSERT INTO app_module (id, module_key, label, code, color, enabled, sort_order, bucket_by_100, bucket_by_type, sync_order) VALUES
 (1,  'tasks',       'Tasks',       'T', '#7cb342', 1, 4,  0, 0, 1),
 (2,  'journals',    'Journal',     'J', '#8764b8', 1, 6,  0, 0, 4),
 (3,  'planners',    'Planner',     'P', '#0078d4', 1, 5,  0, 0, 3),
 (4,  'resources',   'Resources',   'R', '#ef6c00', 1, 8,  1, 1, 7),
 (5,  'notes',       'Notes',       'N', '#00897b', 1, 7,  0, 0, 5),
 (6,  'goals',       'Goals',       'G', '#2e7d32', 1, 3,  0, 0, 2),
 (7,  'writings',    'Writings',    'W', '#c2185b', 1, 9,  0, 0, 6),
 (8,  'departments', 'Departments', 'D', '#5d4037', 1, 1,  0, 0, 99),
 (9,  'courses',     'Courses',     'C', '#1565c0', 1, 2,  0, 0, 99),
 (10, 'indicators',  'Indicators',  'I', '#f9a825', 1, 10, 0, 0, 8),
 (11, 'excerpts',    'Excerpts',    'E', '#6d4c41', 1, 11, 0, 0, 99),
 (12, 'contacts',    'Contacts',    'K', '#00838f', 1, 12, 0, 0, 99);

-- Who may do what. ADMIN holds everything; the other roles are the ones the Users page offers.
INSERT INTO role_permission (role, permission) VALUES
 ('ADMIN', 'content.write'), ('ADMIN', 'params.write'), ('ADMIN', 'tools.admin'),
 ('ADMIN', 'tools.audit'), ('ADMIN', 'tools.settings'), ('ADMIN', 'users.manage'),
 ('DATA_ENTRY', 'content.write'), ('PARAMETERS_DATA_ENTRY', 'params.write');

-- Departments: the broad areas a record belongs to (the one-letter category on notes and tasks).
-- X is the catch-all the phone's quick add files under by default.
INSERT INTO department (id, version, name, summary, code, priority, order_number, date_created, last_updated) VALUES
 (1, 0, 'Studies',       'Studies',       'S', 1, 1, NOW(), NOW()),
 (2, 0, 'Research',      'Research',      'R', 2, 2, NOW(), NOW()),
 (3, 0, 'Career',        'Career',        'C', 2, 3, NOW(), NOW()),
 (4, 0, 'Personal',      'Personal',      'P', 2, 4, NOW(), NOW()),
 (5, 0, 'Health',        'Health',        'H', 2, 5, NOW(), NOW()),
 (6, 0, 'Finance',       'Finance',       'F', 3, 6, NOW(), NOW()),
 (7, 0, 'Miscellaneous', 'Miscellaneous', 'X', 3, 7, NOW(), NOW());

-- Task status. "done" is what the app treats as completed; the active flag marks open work.
INSERT INTO work_status (id, version, name, code, style, marked_as_active, date_created, last_updated) VALUES
 (1, 0, 'Not started', 'not-s',     'background-color:#e3f2fd', 1, NOW(), NOW()),
 (2, 0, 'In progress', 'inp',       'background-color:#c8e6c9', 1, NOW(), NOW()),
 (3, 0, 'Waiting',     'pending',   'background-color:#fff9c4', 1, NOW(), NOW()),
 (4, 0, 'Completed',   'done',      'background-color:#bbdefb', 0, NOW(), NOW()),
 (5, 0, 'Deferred',    'def',       'background-color:#b2ebf2', 0, NOW(), NOW()),
 (6, 0, 'Cancelled',   'dismissed', 'background-color:#ffccbc', 0, NOW(), NOW());

-- Contexts: where a task can be done.
INSERT INTO work_location (id, version, name, code, style, date_created, last_updated) VALUES
 (1, 0, 'Anywhere',   'any',  'background-color:#ffe0b2', NOW(), NOW()),
 (2, 0, 'University', 'uni',  'background-color:#bbdefb', NOW(), NOW()),
 (3, 0, 'Library',    'lib',  'background-color:#e1bee7', NOW(), NOW()),
 (4, 0, 'Home',       'home', 'background-color:#c8e6c9', NOW(), NOW()),
 (5, 0, 'Computer',   'pc',   'background-color:#b2ebf2', NOW(), NOW()),
 (6, 0, 'Errands',    'out',  'background-color:#ffccbc', NOW(), NOW());

-- Planner entries.
INSERT INTO planner_type (id, version, name, code, color, style, date_created, last_updated) VALUES
 (1, 0, 'Lecture',    'lec',  NULL, 'background-color:#bbdefb', NOW(), NOW()),
 (2, 0, 'Study',      'study', NULL, 'background-color:#c8e6c9', NOW(), NOW()),
 (3, 0, 'Exam',       'exam', NULL, 'background-color:#ffcdd2', NOW(), NOW()),
 (4, 0, 'Deadline',   'dln',  NULL, 'background-color:#ffccbc', NOW(), NOW()),
 (5, 0, 'Meeting',    'meet', NULL, 'background-color:#e1bee7', NOW(), NOW()),
 (6, 0, 'Event',      'evn',  NULL, 'background-color:#fff9c4', NOW(), NOW()),
 (7, 0, 'Personal',   'prs',  NULL, 'background-color:#b2dfdb', NOW(), NOW());

-- Journal entries. 'smm' is the one-per-day summary the calendar opens for a day; keep its code.
INSERT INTO journal_type (id, version, name, code, style, date_created, last_updated) VALUES
 (1, 0, 'Day summary', 'smm',   'background-color:#f4f9dc', NOW(), NOW()),
 (2, 0, 'Study log',   'study', 'background-color:#c8e6c9', NOW(), NOW()),
 (3, 0, 'Lecture',     'lec',   'background-color:#bbdefb', NOW(), NOW()),
 (4, 0, 'Reflection',  'ref',   'background-color:#e1bee7', NOW(), NOW()),
 (5, 0, 'Idea',        'idea',  'background-color:#fff9c4', NOW(), NOW());

-- Resources: what a thing is, and where it stands.
INSERT INTO resource_type (id, version, name, code, style, date_created, last_updated) VALUES
 (1, 0, 'Book',          'bk',   'background-color:#ffe0b2', NOW(), NOW()),
 (2, 0, 'E-book',        'ebk',  'background-color:#ffe0b2', NOW(), NOW()),
 (3, 0, 'Article',       'art',  'background-color:#bbdefb', NOW(), NOW()),
 (4, 0, 'Paper',         'ppr',  'background-color:#c5cae9', NOW(), NOW()),
 (5, 0, 'Lecture notes', 'lct',  'background-color:#c8e6c9', NOW(), NOW()),
 (6, 0, 'Slides',        'sld',  'background-color:#dcedc8', NOW(), NOW()),
 (7, 0, 'Video',         'vid',  'background-color:#f8bbd0', NOW(), NOW()),
 (8, 0, 'Course',        'crs',  'background-color:#b2dfdb', NOW(), NOW()),
 (9, 0, 'Web page',      'web',  'background-color:#b3e5fc', NOW(), NOW()),
 (10, 0, 'Document',     'doc',  'background-color:#e0e0e0', NOW(), NOW());

INSERT INTO resource_status (id, version, name, code, style, date_created, last_updated) VALUES
 (1, 0, 'To read',   'todo', 'background-color:#fff9c4', NOW(), NOW()),
 (2, 0, 'Reading',   'inp',  'background-color:#c8e6c9', NOW(), NOW()),
 (3, 0, 'Read',      'done', 'background-color:#bbdefb', NOW(), NOW()),
 (4, 0, 'Reference', 'ref',  'background-color:#e1bee7', NOW(), NOW()),
 (5, 0, 'Dismissed', 'dis',  'background-color:#ffccbc', NOW(), NOW());

-- Goals.
INSERT INTO goal_type (id, version, name, code, style, date_created, last_updated) VALUES
 (1, 0, 'Goal',    'goal', 'background-color:#ffccbc', NOW(), NOW()),
 (2, 0, 'Habit',   'hb',   'background-color:#e1bee7', NOW(), NOW()),
 (3, 0, 'Project', 'prj',  'background-color:#bbdefb', NOW(), NOW());

-- Notes AND writings share these two lists (index_card and writing both point at them), so they
-- are worded to fit both.
INSERT INTO writing_type (id, version, name, code, style, date_created, last_updated) VALUES
 (1, 0, 'Note',          'note',  'background-color:#e0e0e0', NOW(), NOW()),
 (2, 0, 'Idea',          'idea',  'background-color:#fff9c4', NOW(), NOW()),
 (3, 0, 'Lecture notes', 'lec',   'background-color:#bbdefb', NOW(), NOW()),
 (4, 0, 'Summary',       'sum',   'background-color:#c8e6c9', NOW(), NOW()),
 (5, 0, 'Essay',         'essay', 'background-color:#e1bee7', NOW(), NOW()),
 (6, 0, 'Report',        'rpt',   'background-color:#b2ebf2', NOW(), NOW());

INSERT INTO writing_status (id, version, name, code, style, date_created, last_updated) VALUES
 (1, 0, 'New',         'new',  'background-color:#fff9c4', NOW(), NOW()),
 (2, 0, 'In progress', 'inp',  'background-color:#c8e6c9', NOW(), NOW()),
 (3, 0, 'To review',   'rev',  'background-color:#b2ebf2', NOW(), NOW()),
 (4, 0, 'Done',        'done', 'background-color:#bbdefb', NOW(), NOW());

-- What a new record starts on, and which status counts as done.
INSERT INTO app_setting (skey, svalue, updated_at) VALUES
 ('app.name', 'Nibras', NOW()),
 ('task.done-status-id', '4', NOW()),
 ('lookup.default.work_status', '1', NOW()),
 ('lookup.default.work_location', '1', NOW()),
 ('lookup.default.planner_type', '2', NOW()),
 ('lookup.default.journal_type', '1', NOW()),
 ('lookup.default.resource_type', '1', NOW()),
 ('lookup.default.resource_status', '1', NOW()),
 ('lookup.default.goal_type', '1', NOW()),
 ('lookup.default.writing_type', '1', NOW()),
 ('lookup.default.writing_status', '1', NOW()),
 -- Bookmarking a record links its folder into the bookmarks folder the setup wizard names.
 ('bookmark.symlink', 'true', NOW()),
 -- Read by the setup wizard: a fresh install shows it on the first admin sign-in.
 ('setup.completed', 'false', NOW());
