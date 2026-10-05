-- The full address from Keycloak's "address" attribute; postal_code is no longer written.
alter table client
    add column address text;

-- One client per Keycloak user. Keycloak may send the same user several times (upsert by sub_key).
alter table client
    add constraint uk_client_sub_key unique (sub_key);
