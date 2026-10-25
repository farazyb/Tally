alter table wallet rename column sub_key to owner;
alter table wallet rename column idempotency_key to creation_request_id;

-- Postgres keeps the generated constraint names on a column rename; rename them to match.
alter table wallet rename constraint wallet_sub_key_key to wallet_owner_key;
alter table wallet rename constraint wallet_idempotency_key_key to wallet_creation_request_id_key;
