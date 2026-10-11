package com.example.walletservice.outbox;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * Records an event in outbox_event. Debezium (connector wallet-service-outbox) streams the row to Kafka:
 * aggregate_type picks the topic (wallet.&lt;aggregate_type&gt;-events), aggregate_id becomes the message key,
 * event_id and event_type become the "id" and "eventType" headers.
 * <p>
 * MANDATORY: the event must be written in the same transaction as the state change it describes,
 * so both commit or neither does.
 */
@Component
@AllArgsConstructor
public class OutboxWriter {
    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID write(String aggregateType, String aggregateId, String eventType, Object payload) {
        UUID eventId = UUID.randomUUID();
        jdbcClient.sql("""
                        insert into outbox_event (event_id, aggregate_type, aggregate_id, event_type, payload)
                        values (:eventId, :aggregateType, :aggregateId, :eventType, cast(:payload as jsonb))
                        """)
                .param("eventId", eventId)
                .param("aggregateType", aggregateType)
                .param("aggregateId", aggregateId)
                .param("eventType", eventType)
                .param("payload", jsonMapper.writeValueAsString(payload))
                .update();
        return eventId;
    }
}
