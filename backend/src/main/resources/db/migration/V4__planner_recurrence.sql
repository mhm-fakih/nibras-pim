-- Recurring planner events (habits at a fixed time of day), stored as a RULE that is expanded into
-- occurrences at read time rather than materialised into rows up front.
--
-- Why a rule and not a chain: Task recurrence materialises one occurrence at a time and clears the
-- source's `recurring` flag, so exactly one future instance ever exists. That is right for a to-do
-- and wrong for a 07:00 habit — you could never see next Tuesday, and "how many did I miss" would be
-- unanswerable because the misses were never rows. So the calendar expands the rule inside the
-- window it is drawing, and a real row appears only when an occurrence is touched (opened, moved,
-- or given a `reality` note). Same model as iCalendar's RRULE + RECURRENCE-ID + EXDATE.
--
-- The first five columns deliberately reuse task's legacy names (`recurring` holds the PRESET, while
-- `is_recurring` is the on/off switch — confusing, but matching beats a second convention), so the
-- one recurrence step function and the one frontend picker serve both modules.
--
-- Guarded like every other column add in this schema (MySQL has no ADD COLUMN IF NOT EXISTS). Spelt
-- out per column rather than wrapped in a stored procedure: DELIMITER handling differs between
-- Flyway's parser and the mysql client, and this pattern is the one already proven in V1-V3.

-- --- the rule itself, on the series master row -------------------------------------------------
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
                AND table_name = 'planner' AND column_name = 'is_recurring') = 0,
               'ALTER TABLE planner ADD COLUMN is_recurring BIT(1) NULL', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
                AND table_name = 'planner' AND column_name = 'recurring') = 0,
               'ALTER TABLE planner ADD COLUMN recurring VARCHAR(255) NULL', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
                AND table_name = 'planner' AND column_name = 'recurring_interval') = 0,
               'ALTER TABLE planner ADD COLUMN recurring_interval INT NULL', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
                AND table_name = 'planner' AND column_name = 'recurring_cron') = 0,
               'ALTER TABLE planner ADD COLUMN recurring_cron VARCHAR(255) NULL', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
                AND table_name = 'planner' AND column_name = 'permissive_recurring') = 0,
               'ALTER TABLE planner ADD COLUMN permissive_recurring BIT(1) NULL', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- --- where the series stops --------------------------------------------------------------------
-- At most one of the two is set; both null means open-ended, which is the normal case for a habit
-- and is only tractable because occurrences are virtual.
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
                AND table_name = 'planner' AND column_name = 'recurrence_until') = 0,
               'ALTER TABLE planner ADD COLUMN recurrence_until DATETIME NULL', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
                AND table_name = 'planner' AND column_name = 'recurrence_count') = 0,
               'ALTER TABLE planner ADD COLUMN recurrence_count INT NULL', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- --- exceptions --------------------------------------------------------------------------------
-- Occurrences deleted without ever being materialised (EXDATE): newline-separated ISO local
-- datetimes, each the ORIGINAL start of a skipped occurrence.
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
                AND table_name = 'planner' AND column_name = 'recurrence_skips') = 0,
               'ALTER TABLE planner ADD COLUMN recurrence_skips LONGTEXT NULL', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- Set on a materialised occurrence: which series it belongs to, and which occurrence it is. The
-- recurrence_id is the occurrence's ORIGINAL start, so it still identifies the slot after the user
-- drags the event to another time — that is the whole point of storing it separately from start_date.
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
                AND table_name = 'planner' AND column_name = 'series_id') = 0,
               'ALTER TABLE planner ADD COLUMN series_id BIGINT NULL', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
                AND table_name = 'planner' AND column_name = 'recurrence_id') = 0,
               'ALTER TABLE planner ADD COLUMN recurrence_id DATETIME NULL', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- --- indexes -----------------------------------------------------------------------------------
-- Expansion looks up a series' materialised occurrences on every calendar draw...
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
                AND table_name = 'planner' AND index_name = 'idx_planner_series') = 0,
               'CREATE INDEX idx_planner_series ON planner (series_id, recurrence_id)', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ...and finds the rule rows themselves.
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
                AND table_name = 'planner' AND index_name = 'idx_planner_recurring') = 0,
               'CREATE INDEX idx_planner_recurring ON planner (is_recurring)', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
