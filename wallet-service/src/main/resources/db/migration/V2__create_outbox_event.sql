create table outbox_event (
                              event_id       uuid         primary key,
                              aggregate_type varchar(100) not null,
                              aggregate_id   varchar(255) not null,
                              event_type     varchar(100) not null,
                              payload        jsonb        not null,
                              created_at     timestamptz  not null default now()
);
