create sequence wallet_event_sequence start with 1 increment by 50;

create table wallet_event (
    id          bigint       primary key,
    event_id    uuid         not null,
    wallet_id   bigint       not null references wallet (id),
    version     bigint       not null,
    type        varchar(64)  not null,
    payload     jsonb        not null,
    occurred_at timestamptz  not null,
    constraint uk_wallet_event_event_id       unique (event_id),
    constraint uk_wallet_event_wallet_version unique (wallet_id, version)
);
