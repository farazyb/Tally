create sequence wallet_sequence start with 1 increment by 50;

create table wallet (
    id              bigint       primary key,
    sub_key         varchar(255) not null unique,
    idempotency_key varchar(255) not null unique,
    status          varchar(20)  not null,
    account_number  varchar(255),
    created_at      timestamp,
    updated_at      timestamp
);
