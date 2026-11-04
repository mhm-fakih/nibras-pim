-- Expenses on the legacy payment / payment_category tables, in several currencies.

-- The currency an amount is in. NULL = the default currency (Settings: expenses.currency.default),
-- which is how every payment recorded before this column existed is read until it is set.
ALTER TABLE payment ADD COLUMN currency VARCHAR(8) NULL;

-- The currencies offered when recording an expense.
CREATE TABLE currency (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(8) NOT NULL,
  name VARCHAR(100) NULL,
  symbol VARCHAR(16) NULL,
  date_created DATETIME NOT NULL,
  last_updated DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_currency_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Exchange rates over time: 1 from_code = rate to_code, from valid_from on (until a later row for
-- the same pair). Totals convert each expense at the rate in force on its date; a rate entered
-- one way also converts the other way (1/rate).
CREATE TABLE currency_rate (
  id BIGINT NOT NULL AUTO_INCREMENT,
  from_code VARCHAR(8) NOT NULL,
  to_code VARCHAR(8) NOT NULL,
  rate DECIMAL(24,10) NOT NULL,
  valid_from DATE NOT NULL,
  note VARCHAR(255) NULL,
  date_created DATETIME NOT NULL,
  PRIMARY KEY (id),
  KEY idx_rate_pair (from_code, to_code, valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO currency (code, name, symbol, date_created, last_updated) VALUES
  ('USD', 'US dollar', '$', NOW(), NOW()),
  ('EUR', 'Euro', '€', NOW(), NOW()),
  ('LBP', 'Lebanese pound', 'ل.ل', NOW(), NOW());
