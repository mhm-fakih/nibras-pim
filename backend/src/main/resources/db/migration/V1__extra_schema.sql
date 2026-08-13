-- Additive schema for Nibras PIM new features. Creates NEW tables only; never alters
-- the legacy Grails tables (the app runs with hibernate ddl-auto=none).
-- Apply with:  mysql -uuser1 -puser1 db_nibras_dev < db/extra-schema.sql

-- ---------------------------------------------------------------------------
-- Audit trail: one row per changed field on UPDATE; one summary row for
-- INSERT/DELETE. txn_id groups the rows produced by a single change.
-- NOTE: named change_log (NOT audit_log) — a legacy Grails `audit_log` table
-- already exists in the schema and must not be touched.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS change_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    txn_id      VARCHAR(36)  NOT NULL,
    entity      VARCHAR(64)  NOT NULL,
    entity_id   BIGINT       NOT NULL,
    op          VARCHAR(10)  NOT NULL,            -- INSERT | UPDATE | DELETE
    field       VARCHAR(128) NULL,
    old_value   LONGTEXT     NULL,
    new_value   LONGTEXT     NULL,
    changed_at  DATETIME     NOT NULL,
    changed_by  VARCHAR(128) NOT NULL DEFAULT 'system',
    PRIMARY KEY (id),
    KEY idx_change_entity (entity, entity_id),
    KEY idx_change_changed_at (changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- App modules: enable/disable a module's display and give it a short code
-- (used later for command-style interaction). Seeded from the seven entities.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_module (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    module_key  VARCHAR(64)  NOT NULL,
    label       VARCHAR(128) NOT NULL,
    code        VARCHAR(8)   NULL,
    color       VARCHAR(16)  NULL,
    enabled     TINYINT(1)   NOT NULL DEFAULT 1,
    sort_order  INT          NOT NULL DEFAULT 0,
    -- Per-module record-folder layout under repository 1 (~/rps1): nest the record under an id/100
    -- bucket folder, and/or under its type-code folder. Resources use both (R/<type>/<id100>/<id>).
    bucket_by_100  TINYINT(1) NOT NULL DEFAULT 0,
    bucket_by_type TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_app_module_key (module_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Idempotently add the bucketing columns to an already-existing app_module table.
SET @add100 := IF((SELECT COUNT(*) FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'app_module'
                     AND column_name = 'bucket_by_100') = 0,
                  'ALTER TABLE app_module ADD COLUMN bucket_by_100 TINYINT(1) NOT NULL DEFAULT 0',
                  'DO 0');
PREPARE s FROM @add100; EXECUTE s; DEALLOCATE PREPARE s;
SET @addtype := IF((SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'app_module'
                      AND column_name = 'bucket_by_type') = 0,
                   'ALTER TABLE app_module ADD COLUMN bucket_by_type TINYINT(1) NOT NULL DEFAULT 0',
                   'DO 0');
PREPARE s FROM @addtype; EXECUTE s; DEALLOCATE PREPARE s;

-- Idempotently add the per-module accent colour (shown as the list-row bar).
SET @addcolor := IF((SELECT COUNT(*) FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = 'app_module'
                       AND column_name = 'color') = 0,
                    'ALTER TABLE app_module ADD COLUMN color VARCHAR(16) NULL AFTER code',
                    'DO 0');
PREPARE s FROM @addcolor; EXECUTE s; DEALLOCATE PREPARE s;

INSERT INTO app_module (module_key, label, code, enabled, sort_order) VALUES
    ('tasks',     'Tasks',     'T', 1, 1),
    ('journals',  'Journal',   'J', 1, 2),
    ('planners',  'Planner',   'P', 1, 3),
    ('resources', 'Resources', 'R', 1, 4),
    ('notes',     'Notes',     'N', 1, 5),
    ('goals',     'Goals',     'G', 1, 6),
    ('writings',  'Writings',  'W', 1, 7),
    ('departments', 'Departments', 'D', 1, 8),
    ('courses',     'Courses',     'C', 1, 9),
    ('indicators',  'Indicators',  'I', 1, 10),
    ('excerpts',    'Excerpts',    'E', 1, 11)
ON DUPLICATE KEY UPDATE module_key = module_key;   -- idempotent: no-op if already seeded

-- Default folder layout: only Resources nest by type + id/100 (R/<type>/<id100>/<id>); all others are
-- flat (<code>/<id>). One-time seed: runs only when nothing has been configured yet, so re-running the
-- script never clobbers the user's later per-module choices.
SET @seed := IF((SELECT COUNT(*) FROM app_module WHERE bucket_by_type = 1 OR bucket_by_100 = 1) = 0,
                'UPDATE app_module SET bucket_by_100 = 1, bucket_by_type = 1 WHERE module_key = ''resources''',
                'DO 0');
PREPARE s FROM @seed; EXECUTE s; DEALLOCATE PREPARE s;

-- One-time seed of the per-module accent colours (only fills rows that have none yet, so the
-- user's later colour choices are never overwritten).
UPDATE app_module SET color = CASE module_key
        WHEN 'tasks'       THEN '#7cb342'   -- light green
        WHEN 'journals'    THEN '#8764b8'   -- purple
        WHEN 'planners'    THEN '#0078d4'   -- blue
        WHEN 'resources'   THEN '#ef6c00'   -- orange
        WHEN 'notes'       THEN '#00897b'   -- teal
        WHEN 'goals'       THEN '#2e7d32'   -- dark green
        WHEN 'writings'    THEN '#c2185b'   -- pink
        WHEN 'departments' THEN '#5d4037'   -- brown
        WHEN 'courses'     THEN '#1565c0'   -- indigo blue
        WHEN 'indicators'  THEN '#f9a825'   -- amber
        WHEN 'excerpts'    THEN '#6d4c41'   -- book brown
        ELSE color END
    WHERE color IS NULL;

-- ---------------------------------------------------------------------------
-- app_setting: clean key/value store for new-app, user-editable configuration
-- (repository root paths repo1/2/3, the file-explorer command, …). Separate
-- from the cluttered legacy `setting` table; defaults live in code so this can
-- start empty. Additive — nothing to migrate.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_setting (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    skey       VARCHAR(128) NOT NULL,
    svalue     LONGTEXT     NULL,
    category   VARCHAR(64)  NULL,
    label      VARCHAR(255) NULL,
    updated_at DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_app_setting_key (skey)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Full-text search index across every module. Kept in sync on create/update/
-- delete and rebuildable via POST /api/search/reindex. Separate table so the
-- legacy tables stay untouched.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS search_doc (
    module      VARCHAR(32) NOT NULL,
    record_id   BIGINT      NOT NULL,
    title       TEXT        NULL,
    body        LONGTEXT    NULL,
    updated_at  DATETIME    NOT NULL,
    PRIMARY KEY (module, record_id),
    FULLTEXT KEY ft_search (title, body)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Legacy-table change (no data loss): resources (`book`) gains a `short_description`
-- column so it has the same short-summary field writings/notes already carry. Used as
-- the listing blurb by the static-site generator. Additive nullable column — safe.
-- ---------------------------------------------------------------------------
SET @addbookshort := IF((SELECT COUNT(*) FROM information_schema.columns
                         WHERE table_schema = DATABASE() AND table_name = 'book'
                           AND column_name = 'short_description') = 0,
                        'ALTER TABLE book ADD COLUMN short_description LONGTEXT NULL',
                        'DO 0');
PREPARE s FROM @addbookshort; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- Legacy-table change (no data loss): indicators gain a `script` column — a shell command the
-- backend runs to propose a value (e.g. folder size). Additive nullable column — safe.
-- ---------------------------------------------------------------------------
SET @addindscript := IF((SELECT COUNT(*) FROM information_schema.columns
                         WHERE table_schema = DATABASE() AND table_name = 'indicators'
                           AND column_name = 'script') = 0,
                        'ALTER TABLE indicators ADD COLUMN script LONGTEXT NULL',
                        'DO 0');
PREPARE s FROM @addindscript; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- Legacy-table change (no data loss): indicators gain `category` (a short code used to overlay
-- several indicators' series on one chart with a legend, e.g. 'Ta' = tasks added/completed today)
-- and `color` (the line colour on the chart; auto-filled from a palette when left blank).
-- ---------------------------------------------------------------------------
SET @addindcat := IF((SELECT COUNT(*) FROM information_schema.columns
                      WHERE table_schema = DATABASE() AND table_name = 'indicators'
                        AND column_name = 'category') = 0,
                     'ALTER TABLE indicators ADD COLUMN category VARCHAR(255) NULL',
                     'DO 0');
PREPARE s FROM @addindcat; EXECUTE s; DEALLOCATE PREPARE s;

SET @addindcolor := IF((SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = DATABASE() AND table_name = 'indicators'
                          AND column_name = 'color') = 0,
                       'ALTER TABLE indicators ADD COLUMN color VARCHAR(255) NULL',
                       'DO 0');
PREPARE s FROM @addindcolor; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- Application accounts (login + roles). Separate from any legacy Grails `user`
-- table. Passwords are BCrypt hashes. Roles: ADMIN, DATA_ENTRY,
-- PARAMETERS_DATA_ENTRY, READ_ONLY. A default `admin` account is auto-seeded by
-- the app on first run when this table is empty.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(128) NULL,
    role          VARCHAR(32)  NOT NULL,
    enabled       BIT(1)       NOT NULL DEFAULT b'1',
    date_created  DATETIME     NOT NULL,
    last_updated  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------------
-- Role→capability matrix (which permission keys each role holds). The key
-- vocabulary is fixed in code (com.nibras.pim.security.Permissions); this table
-- is the editable assignment, seeded with defaults by the app on first run.
-- Also: app_user.must_change_password forces a password change on next login.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS role_permission (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    role        VARCHAR(32) NOT NULL,
    permission  VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_permission (role, permission)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @addmcp := IF((SELECT COUNT(*) FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'app_user'
                     AND column_name = 'must_change_password') = 0,
                  'ALTER TABLE app_user ADD COLUMN must_change_password BIT(1) NOT NULL DEFAULT b''0''',
                  'DO 0');
PREPARE s FROM @addmcp; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- Repair a corrupt legacy constraint: task.goal_id carries a stray Grails-era
-- foreign key referencing writing(id) (alongside the correct FKs to goal(id)),
-- which rejects every INSERT/UPDATE that links a task to a goal. Exception to
-- the "never alter legacy tables" rule above: this only DROPS the provably
-- wrong constraint — no column or data changes.
-- ---------------------------------------------------------------------------
SET @badfk := (SELECT constraint_name FROM information_schema.key_column_usage
               WHERE table_schema = DATABASE() AND table_name = 'task'
                 AND column_name = 'goal_id' AND referenced_table_name = 'writing'
               LIMIT 1);
SET @dropfk := IF(@badfk IS NULL, 'DO 0',
                  CONCAT('ALTER TABLE task DROP FOREIGN KEY `', @badfk, '`'));
PREPARE s FROM @dropfk; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- Legacy-table change (no data loss): tasks gain an `urgent` flag, independent of
-- `priority` (Eisenhower-style urgency axis, separate from the existing importance
-- dial). Everything else needed for recurrence/subtasks already exists unmapped on
-- `task` (order_number, is_recurring, recurring, recurring_interval, recurring_cron,
-- permissive_recurring) — only this one column is genuinely new. Additive
-- nullable column — safe.
-- ---------------------------------------------------------------------------
SET @addtaskurgent := IF((SELECT COUNT(*) FROM information_schema.columns
                          WHERE table_schema = DATABASE() AND table_name = 'task'
                            AND column_name = 'urgent') = 0,
                         'ALTER TABLE task ADD COLUMN urgent BIT(1) NULL',
                         'DO 0');
PREPARE s FROM @addtaskurgent; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- Indicators gain an `auto_collect` flag: when set, IndicatorAutoCollectScheduler computes and
-- saves the indicator's value automatically (query/file-count/script) at a configurable end-of-day
-- time, instead of only surfacing it for manual confirmation on the Indicators page. Additive
-- nullable column — safe.
-- ---------------------------------------------------------------------------
SET @addindicatorautocollect := IF((SELECT COUNT(*) FROM information_schema.columns
                          WHERE table_schema = DATABASE() AND table_name = 'indicators'
                            AND column_name = 'auto_collect') = 0,
                         'ALTER TABLE indicators ADD COLUMN auto_collect BIT(1) NULL',
                         'DO 0');
PREPARE s FROM @addindicatorautocollect; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- Indicators gain an optional `goal_id` link: a daily-frequency indicator linked to a Goal is
-- how a habit's day-by-day log / heatmap (shown in the goal's detail view) is built. No FK
-- constraint (consistent with how other nullable link columns were added here) — enforced at
-- the app layer like every other ManyToOne. Additive nullable column — safe.
-- ---------------------------------------------------------------------------
SET @addindicatorgoal := IF((SELECT COUNT(*) FROM information_schema.columns
                          WHERE table_schema = DATABASE() AND table_name = 'indicators'
                            AND column_name = 'goal_id') = 0,
                         'ALTER TABLE indicators ADD COLUMN goal_id BIGINT NULL',
                         'DO 0');
PREPARE s FROM @addindicatorgoal; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- Record-merge feature: each of the 7 mergeable record types gains a `merged_into_id` column.
-- When two records of the same type are merged, the secondary is soft-deleted (deleted_on set,
-- as usual) and merged_into_id is stamped with the surviving primary's id, so a trash-bin view can
-- show "merged into #<id>" instead of a plain delete. No FK constraint, same convention as the
-- other nullable link columns above. Additive nullable column — safe.
-- ---------------------------------------------------------------------------
SET @addtaskmerged := IF((SELECT COUNT(*) FROM information_schema.columns
                          WHERE table_schema = DATABASE() AND table_name = 'task'
                            AND column_name = 'merged_into_id') = 0,
                         'ALTER TABLE task ADD COLUMN merged_into_id BIGINT NULL',
                         'DO 0');
PREPARE s FROM @addtaskmerged; EXECUTE s; DEALLOCATE PREPARE s;

SET @addjournalmerged := IF((SELECT COUNT(*) FROM information_schema.columns
                          WHERE table_schema = DATABASE() AND table_name = 'journal'
                            AND column_name = 'merged_into_id') = 0,
                         'ALTER TABLE journal ADD COLUMN merged_into_id BIGINT NULL',
                         'DO 0');
PREPARE s FROM @addjournalmerged; EXECUTE s; DEALLOCATE PREPARE s;

SET @addplannermerged := IF((SELECT COUNT(*) FROM information_schema.columns
                          WHERE table_schema = DATABASE() AND table_name = 'planner'
                            AND column_name = 'merged_into_id') = 0,
                         'ALTER TABLE planner ADD COLUMN merged_into_id BIGINT NULL',
                         'DO 0');
PREPARE s FROM @addplannermerged; EXECUTE s; DEALLOCATE PREPARE s;

SET @addbookmerged := IF((SELECT COUNT(*) FROM information_schema.columns
                          WHERE table_schema = DATABASE() AND table_name = 'book'
                            AND column_name = 'merged_into_id') = 0,
                         'ALTER TABLE book ADD COLUMN merged_into_id BIGINT NULL',
                         'DO 0');
PREPARE s FROM @addbookmerged; EXECUTE s; DEALLOCATE PREPARE s;

SET @addindexcardmerged := IF((SELECT COUNT(*) FROM information_schema.columns
                          WHERE table_schema = DATABASE() AND table_name = 'index_card'
                            AND column_name = 'merged_into_id') = 0,
                         'ALTER TABLE index_card ADD COLUMN merged_into_id BIGINT NULL',
                         'DO 0');
PREPARE s FROM @addindexcardmerged; EXECUTE s; DEALLOCATE PREPARE s;

SET @addgoalmerged := IF((SELECT COUNT(*) FROM information_schema.columns
                          WHERE table_schema = DATABASE() AND table_name = 'goal'
                            AND column_name = 'merged_into_id') = 0,
                         'ALTER TABLE goal ADD COLUMN merged_into_id BIGINT NULL',
                         'DO 0');
PREPARE s FROM @addgoalmerged; EXECUTE s; DEALLOCATE PREPARE s;

SET @addwritingmerged := IF((SELECT COUNT(*) FROM information_schema.columns
                          WHERE table_schema = DATABASE() AND table_name = 'writing'
                            AND column_name = 'merged_into_id') = 0,
                         'ALTER TABLE writing ADD COLUMN merged_into_id BIGINT NULL',
                         'DO 0');
PREPARE s FROM @addwritingmerged; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- Goals gain a `dashboard` flag: goals marked this way show their linked habit indicators'
-- heatmaps in the dashboard builder's "goalsHeatmap" component (see dashboard/dashboard_region
-- tables below). Additive nullable column — safe.
-- ---------------------------------------------------------------------------
SET @addgoaldashboard := IF((SELECT COUNT(*) FROM information_schema.columns
                          WHERE table_schema = DATABASE() AND table_name = 'goal'
                            AND column_name = 'dashboard') = 0,
                         'ALTER TABLE goal ADD COLUMN dashboard BIT(1) NULL',
                         'DO 0');
PREPARE s FROM @addgoaldashboard; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- Dashboard builder: user-defined dashboards made of typed "region" components (quote-of-the-day,
-- a filtered calendar, the goals habit heatmap, a file's content, …), arranged in a simple grid
-- (dashboard.columns_count wide, regions filling left-to-right/top-to-bottom by order_index).
-- Each region's type-specific settings (e.g. which note type, which repo/file) live in `config` as
-- a small JSON blob the frontend owns the shape of — the backend just stores/returns it.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS dashboard (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    version       BIGINT       NOT NULL DEFAULT 0,
    name          VARCHAR(255) NOT NULL,
    columns_count INT          NOT NULL DEFAULT 2,
    date_created  DATETIME     NOT NULL,
    last_updated  DATETIME     NOT NULL,
    deleted_on    DATETIME     NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS dashboard_region (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    dashboard_id    BIGINT       NOT NULL,
    order_index     INT          NOT NULL DEFAULT 0,
    component_type  VARCHAR(64)  NOT NULL,
    config          LONGTEXT     NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    date_created    DATETIME     NOT NULL,
    last_updated    DATETIME     NOT NULL,
    deleted_on      DATETIME     NULL,
    PRIMARY KEY (id),
    KEY idx_dashboard_region_dashboard (dashboard_id),
    CONSTRAINT fk_dashboard_region_dashboard FOREIGN KEY (dashboard_id)
        REFERENCES dashboard (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Dashboards gain a `refresh_minutes` column (added after the table above — CREATE TABLE IF NOT
-- EXISTS won't add it to an already-created table, hence the guarded ALTER here instead of just
-- adding it to the CREATE above). Minutes between auto full-page-reloads of the chrome-free view;
-- null/non-positive disables it.
-- ---------------------------------------------------------------------------
SET @adddashboardrefresh := IF((SELECT COUNT(*) FROM information_schema.columns
                          WHERE table_schema = DATABASE() AND table_name = 'dashboard'
                            AND column_name = 'refresh_minutes') = 0,
                         'ALTER TABLE dashboard ADD COLUMN refresh_minutes INT NULL DEFAULT 5',
                         'DO 0');
PREPARE s FROM @adddashboardrefresh; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- Dashboard regions gain a `col_span` column — how many of the dashboard's grid columns this
-- region occupies (e.g. a calendar spanning 3 of 4 columns, a heatmap taking just 1). Defaults to
-- 1 (today's behaviour, unchanged for every existing region).
-- ---------------------------------------------------------------------------
SET @adddashboardregioncolspan := IF((SELECT COUNT(*) FROM information_schema.columns
                          WHERE table_schema = DATABASE() AND table_name = 'dashboard_region'
                            AND column_name = 'col_span') = 0,
                         'ALTER TABLE dashboard_region ADD COLUMN col_span INT NOT NULL DEFAULT 1',
                         'DO 0');
PREPARE s FROM @adddashboardregioncolspan; EXECUTE s; DEALLOCATE PREPARE s;
