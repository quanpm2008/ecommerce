ALTER TABLE users
    ADD COLUMN banned_at timestamptz,
    ADD COLUMN disabled_at timestamptz,
    ADD COLUMN restore_until timestamptz;
