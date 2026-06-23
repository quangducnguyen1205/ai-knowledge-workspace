ALTER TABLE user_accounts
    ADD COLUMN identity_provider VARCHAR(255);

ALTER TABLE user_accounts
    ADD COLUMN external_subject VARCHAR(255);

ALTER TABLE user_accounts
    ADD CONSTRAINT ck_user_accounts_external_identity_pair
        CHECK (
            (identity_provider IS NULL AND external_subject IS NULL)
            OR (identity_provider IS NOT NULL AND external_subject IS NOT NULL)
        );

ALTER TABLE user_accounts
    ADD CONSTRAINT uk_user_accounts_external_identity
        UNIQUE (identity_provider, external_subject);
