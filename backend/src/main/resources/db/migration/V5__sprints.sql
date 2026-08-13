-- Sprints: a named, dated block of committed work, drawn from the bookmarked tasks.
--
-- Two tables, not a `sprint_id` column on `task`, and that is the whole design decision. A single
-- FK records where a task is NOW; the requirement is to know how many times a task has been
-- committed to a sprint and NOT finished, which is a question about its history. A task carried
-- from Monday's sprint into Tuesday's into Thursday's has three memberships and two misses, and a
-- column can only ever remember the last one. So membership is its own row, and it carries the
-- outcome.
--
-- Note what is NOT here: a new column for a task's expected duration. `task.planned_duration`
-- (float(9,3)) has been on the table since the legacy schema and is referenced by nothing in this
-- codebase — no entity, no DTO, no form. 15 rows carry a value, between 1 and 15. It is read as
-- HOURS: the three decimal places only make sense for a fractional unit, and a max of 15 is a
-- long task in hours but an implausibly precise one in minutes. Exposing it is strictly better
-- than adding a second column that means the same thing.

CREATE TABLE IF NOT EXISTS sprint (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    version        BIGINT       NOT NULL DEFAULT 0,
    summary        VARCHAR(255) NULL,
    notes          LONGTEXT     NULL,
    -- Sprints are usually same-day, so both ends carry a time, not just a date.
    start_date     DATETIME     NULL,
    end_date       DATETIME     NULL,
    -- Stamped when the review is done, so an un-reviewed past sprint is distinguishable from one
    -- that was reviewed and simply had misses. Null = still open.
    closed_on      DATETIME     NULL,
    date_created   DATETIME     NOT NULL,
    last_updated   DATETIME     NOT NULL,
    deleted_on     DATETIME     NULL,
    PRIMARY KEY (id),
    KEY idx_sprint_start (start_date),
    KEY idx_sprint_closed (closed_on)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sprint_task (
    id             BIGINT   NOT NULL AUTO_INCREMENT,
    sprint_id      BIGINT   NOT NULL,
    task_id        BIGINT   NOT NULL,
    -- The outcome of THIS membership. False on a closed sprint is a miss; false on an open one is
    -- simply "not done yet" — which is why closed_on above has to exist.
    completed      BIT(1)   NOT NULL DEFAULT b'0',
    completed_on   DATETIME NULL,
    -- What the task was expected to take when it was committed, in MINUTES. Copied rather than read
    -- live from task.planned_duration, so re-estimating a task later doesn't silently rewrite what
    -- a past sprint was planned to be. Null means the task had no estimate and the fallback was
    -- used — which is what lets the warning name the tasks that were guessed for. See SprintService.
    planned_minutes INT     NULL,
    sort_order     INT      NULL,
    date_created   DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sprint_task (sprint_id, task_id),
    KEY idx_sprint_task_task (task_id),
    CONSTRAINT fk_sprint_task_sprint FOREIGN KEY (sprint_id) REFERENCES sprint (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
