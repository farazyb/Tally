-- W3C traceparent of the request that wrote the event; Debezium copies it into the "traceparent" Kafka header.
alter table outbox_event add column traceparent varchar(100);
