-- Payments entered on the phone while offline are sent when it is back online. Each carries the
-- phone's own id for it (client_ref), so a send repeated after a lost reply finds the payment it
-- already made instead of recording the same money twice.
ALTER TABLE payment ADD COLUMN client_ref VARCHAR(64) NULL;
CREATE UNIQUE INDEX ux_payment_client_ref ON payment (client_ref);
