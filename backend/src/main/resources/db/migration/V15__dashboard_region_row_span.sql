-- How many grid rows a dashboard region spans (a calendar beside two smaller components). Null = 1.
ALTER TABLE dashboard_region ADD COLUMN row_span INT NULL;
