-- One client per profile key; the unique index also serves lookups by profile key.
-- NULLs (clients created before V5) are allowed and never conflict with each other.
alter table client
    add constraint uk_client_profile_key unique (profile_key);
