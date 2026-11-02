-- Dashboard column widths: comma-separated relative weights, one per column ("1,2,1" makes the
-- middle column twice as wide as the others). Null means equal columns. Set by dragging the
-- separators on the dashboard's view page, or typed in the builder.
ALTER TABLE dashboard ADD COLUMN column_widths VARCHAR(200) NULL;
