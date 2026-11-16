ALTER TABLE rentals
    ADD COLUMN daily_rate_cents integer,
    ADD COLUMN fine_amount_cents integer,
    ADD COLUMN billed_days bigint,
    ADD COLUMN late_days bigint;

-- Open rentals have not been charged yet; preserve the currently agreed prices at upgrade.
UPDATE rentals r
SET daily_rate_cents = c.daily_rate_cents,
    fine_amount_cents = c.fine_amount_cents
FROM cars c
WHERE r.car_id = c.id AND r.end_date IS NULL;

-- Old receipts retain their recorded total. Their historical price breakdown is unknown.
ALTER TABLE rentals
    ADD CONSTRAINT rentals_snapshot_nonnegative CHECK (
        (daily_rate_cents IS NULL OR daily_rate_cents >= 0) AND
        (fine_amount_cents IS NULL OR fine_amount_cents >= 0) AND
        (billed_days IS NULL OR billed_days >= 1) AND
        (late_days IS NULL OR late_days >= 0)
    ),
    ADD CONSTRAINT rentals_new_receipt_complete CHECK (
        daily_rate_cents IS NULL OR end_date IS NULL OR
        (fine_amount_cents IS NOT NULL AND billed_days IS NOT NULL AND late_days IS NOT NULL)
    );
