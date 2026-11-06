-- App usage indicators: one auto-collected daily indicator per program of the suite.
--
-- indicators.usage_app names the program whose usage the indicator counts (kind USAGE). The
-- desktop programs append one line per action to <usage dir>/<app>.log (see AppUsageService);
-- "backend" is counted from the database itself (records added or updated). All eight share the
-- category 'Apps', which the Indicators page shows as its own "App usage" section.
ALTER TABLE indicators ADD COLUMN usage_app VARCHAR(64) NULL;

INSERT INTO indicators (version, date_created, last_updated, code, name, description, frequency,
                        scale, category, usage_app, bookmarked, auto_collect, priority)
SELECT 0, now(), now(), s.code, s.name, s.description, 'daily', s.scale, 'Apps', s.app,
       b'0', b'1', s.ord
FROM (SELECT 'app_terminal' code, 'Terminal: notes added' name,
             'Notes saved from the knotes terminal.' description, 'notes' scale, 'terminal' app, 1 ord
      UNION ALL SELECT 'app_desktop', 'Desktop: notes added',
             'Notes added in the knotes desktop app.', 'notes', 'desktop', 2
      UNION ALL SELECT 'app_audio_player', 'Audio player: time marks added',
             'Bookmarks (time marks) added in the audio player.', 'marks', 'audio-player', 3
      UNION ALL SELECT 'app_scr_annotator', 'Screen annotator: images annotated',
             'Screenshots saved from the screen annotator.', 'images', 'scr-annotator', 4
      UNION ALL SELECT 'app_telegram', 'Telegram capture: messages captured',
             'Messages written to knotes by the Telegram capture.', 'messages', 'telegram', 5
      UNION ALL SELECT 'app_firefox', 'Firefox capture: bookmarks captured',
             'Bookmarks written to knotes by the Firefox capture.', 'bookmarks', 'firefox', 6
      UNION ALL SELECT 'app_recorder', 'Recorder: recordings merged',
             'Recorder transcripts merged into a note.', 'recordings', 'recorder', 7
      UNION ALL SELECT 'app_backend', 'Backend: records added or updated',
             'Records created or changed in the database (any module).', 'records', 'backend', 8) s
WHERE NOT EXISTS (SELECT 1 FROM indicators i WHERE i.code = s.code AND i.deleted_on IS NULL);
