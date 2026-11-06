-- Budgeted (to-be-paid) expenses and lessons learned.
--
-- payment.paid_on (a legacy column, never filled before) now says whether an expense is PAID:
-- set = paid on that day; NULL = to be paid — a budgeted payment, counted apart from what was
-- spent. Every expense recorded so far was a payment made, so each gets paid_on = its date.
UPDATE payment SET paid_on = coalesce(date, date_created) WHERE paid_on IS NULL;

-- What a payment taught: a lesson to read back later (Expenses → Lessons).
ALTER TABLE payment ADD COLUMN lesson LONGTEXT NULL;
