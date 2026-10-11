package com.example.walletservice.outbox;

import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Records an event in outbox_event. Debezium (connector wallet-service-outbox) streams the row to Kafka:
 * aggregate_type picks the topic (wallet.&lt;aggregate_type&gt;-events), aggregate_id becomes the message key,
 * event_id, event_type and traceparent become the "id", "eventType" and "traceparent" headers.
 * The traceparent lets consumers continue the current trace, so their log lines share its traceId.
 * <p>
 * MANDATORY: the event must be written in the same transaction as the state change it describes,
 * so both commit or neither does.
 */
@Slf4j
@Component
@AllArgsConstructor
public class OutboxWriter {
    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;
    private final ObjectProvider<Tracer> tracer;
    private final ObjectProvider<Propagator> propagator;

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID write(String aggregateType, String aggregateId, String eventType, Object payload) {
        UUID eventId = UUID.randomUUID();
        jdbcClient.sql("""
                        insert into outbox_event (event_id, aggregate_type, aggregate_id, event_type, payload, traceparent)
                        values (:eventId, :aggregateType, :aggregateId, :eventType, cast(:payload as jsonb), :traceparent)
                        """)
                .param("eventId", eventId)
                .param("aggregateType", aggregateType)
                .param("aggregateId", aggregateId)
                .param("eventType", eventType)
                .param("payload", jsonMapper.writeValueAsString(payload))
                .param("traceparent", currentTraceparent())
                .update();
        log.atDebug()
                .addKeyValue("eventId", eventId)
                .addKeyValue("eventType", eventType)
                .addKeyValue("aggregateId", aggregateId)
                .log("Outbox event written");
        return eventId;
    }

    /** W3C traceparent of the current span, or null when there is no active trace. */
    private String currentTraceparent() {
        Tracer currentTracer = tracer.getIfAvailable();
        Propagator currentPropagator = propagator.getIfAvailable();
        TraceContext context = currentTracer == null ? null : currentTracer.currentTraceContext().context();
        if (context == null || currentPropagator == null) {
            return null;
        }
        Map<String, String> carrier = new HashMap<>();
        currentPropagator.inject(context, carrier, Map::put);
        return carrier.get("traceparent");
    }
}
