-- Which task statuses count as "active" (still open): the legacy work_status.marked_as_active
-- flag, now used by the Tasks list's Active / Not active filter. Set by code, so it means the same
-- on every copy of the database. Active: Needs Action, In progress, Almost completed, Pending,
-- Feared, Ready, Assigned. Everything else (Completed, Cancelled, Deferred, Waiting, Frozen,
-- Not reported, converted to writing, …) is not active.
UPDATE work_status
SET marked_as_active = (code IN ('not-s', 'inp', 'al', 'pending', 'fear', 'ready', 'Assigned'));
