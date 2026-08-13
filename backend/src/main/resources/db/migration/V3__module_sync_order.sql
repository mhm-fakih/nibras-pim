-- Order in which a module's parameters are handed to the clients (mobile / terminal / desktop), so
-- the Admin "synced to clients" list — and anything that later drives the clients themselves —
-- reads in a deliberate order rather than by id. Separate from sort_order, which is the navigation
-- order in the sidebar: the two are not the same preference and were already diverging by hand.
--
-- Guarded like every other column add in this schema (MySQL has no ADD COLUMN IF NOT EXISTS).
SET @add_sync_order := IF((SELECT COUNT(*) FROM information_schema.columns
                           WHERE table_schema = DATABASE() AND table_name = 'app_module'
                             AND column_name = 'sync_order') = 0,
                          'ALTER TABLE app_module ADD COLUMN sync_order INT NOT NULL DEFAULT 0',
                          'DO 0');
PREPARE s FROM @add_sync_order; EXECUTE s; DEALLOCATE PREPARE s;

-- Seed the documented default order: T G P J N W R I (tasks, goals, planner, journal, notes,
-- writings, resources, indicators). Matched on the module's `code` letter so a renamed label or a
-- reordered sidebar doesn't disturb it. Only applied where nothing has been set yet (sync_order = 0),
-- so re-running never overwrites an order the user has since chosen.
UPDATE app_module SET sync_order = CASE code
    WHEN 'T' THEN 1
    WHEN 'G' THEN 2
    WHEN 'P' THEN 3
    WHEN 'J' THEN 4
    WHEN 'N' THEN 5
    WHEN 'W' THEN 6
    WHEN 'R' THEN 7
    WHEN 'I' THEN 8
    ELSE 99
END
WHERE sync_order = 0;
