-- Relative row heights of a custom dashboard, comma-separated ("1,2"), set by dragging the
-- separator between two stacked components on the view page. Null = equal rows.
ALTER TABLE dashboard ADD COLUMN row_heights VARCHAR(200) NULL;
