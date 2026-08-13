-- task.planned_duration is MINUTES, as an integer. This supersedes the note in V5__sprints.sql,
-- which read the legacy float as hours — that file is left untouched on purpose: it has already
-- been applied, and Flyway validates its checksum on every startup.
--
-- Minutes is the better unit because of what a sprint is for: tasks small enough to be estimated at
-- 10, 20, 30, 45, 60 minutes. Hours push you toward "about half a day", which is the granularity a
-- sprint exists to break down. An integer says the same thing in the type — there is no such
-- estimate as 12.5 minutes.
--
-- The 15 legacy values are CLEARED rather than converted. They were entered before anything read
-- this column and they are not in one unit: some were meant as minutes and some as hours, and
-- nothing on the row says which. "دعاء طويل كل يوم" at 15 is fifteen minutes; "Settle bookmarked
-- records" at 4 is plausibly four hours. Any single multiplier is therefore wrong for part of the
-- set, and a wrong estimate is worse than an absent one: absent is visible — the sprint counts the
-- task at the default and names it in the warning — whereas a wrong number is silently believed and
-- quietly wrecks the plan it feeds. Ten of the fifteen are on completed tasks anyway; the five that
-- are still open take a moment to re-estimate, and now have a form field to do it in.

UPDATE task SET planned_duration = NULL WHERE planned_duration IS NOT NULL;

SET @ddl := IF(
    (SELECT DATA_TYPE FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'task' AND COLUMN_NAME = 'planned_duration') = 'int',
    'SELECT 1',
    'ALTER TABLE task MODIFY COLUMN planned_duration INT NULL');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
