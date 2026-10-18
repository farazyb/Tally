package com.example.common.outbox;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class OutboxConfiguration {

    @Bean
    OutboxWriter outboxWriter(JdbcClient jdbcClient, JsonMapper jsonMapper,
                              ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        return new OutboxWriter(jdbcClient, jsonMapper, tracer, propagator);
    }
}
