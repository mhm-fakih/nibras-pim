-- Register Contacts as a module so it appears in the nav and in Settings → Modules.
--
-- The `contact` TABLE is legacy and untouched — this adds no columns and creates nothing. All that
-- is new is the row that tells the app the module exists, which is what every other module has.
--
-- Why it is needed at all: `app_module` drives the left rail and the module list, so a module that
-- is only declared in the frontend's entities.ts never appears. Idempotent, because a migration
-- that fails on a re-run blocks every later one.
INSERT INTO app_module (module_key, label, code, color, enabled, sort_order, bucket_by_100, bucket_by_type, sync_order)
SELECT 'contacts', 'Contacts', 'K', '#00838f', 1,
       COALESCE((SELECT MAX(m.sort_order) FROM (SELECT sort_order FROM app_module) m), 0) + 1,
       0, 0, 99
WHERE NOT EXISTS (SELECT 1 FROM (SELECT module_key FROM app_module) x WHERE x.module_key = 'contacts');
