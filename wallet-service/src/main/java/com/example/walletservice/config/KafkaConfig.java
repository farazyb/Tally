package com.example.walletservice.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import tools.jackson.core.JacksonException;

@Configuration
public class KafkaConfig {

    /**
     * Failed records are retried with backoff (1s, 2s, 4s, 8s), then published to "&lt;topic&gt;.DLT".
     * Malformed events go to the DLT right away, since retrying cannot fix them.
     * Spring Boot applies this handler to the auto-configured listener container factory.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        // Partition -1: let Kafka pick, so the DLT does not need as many partitions as the source topic.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1));

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(4);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class, JacksonException.class);
        errorHandler.setRetryListeners(new LoggingRetryListener());
        return errorHandler;
    }

    /** Logs each failed delivery attempt, and the final outcome: sent to the DLT, or the DLT publish failed. */
    @Slf4j
    static class LoggingRetryListener implements RetryListener {
        @Override
        public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
            log.atWarn()
                    .addKeyValue("topic", record.topic())
                    .addKeyValue("partition", record.partition())
                    .addKeyValue("offset", record.offset())
                    .addKeyValue("deliveryAttempt", deliveryAttempt)
                    .setCause(ex)
                    .log("Kafka record processing failed");
        }

        @Override
        public void recovered(ConsumerRecord<?, ?> record, Exception ex) {
            log.atError()
                    .addKeyValue("topic", record.topic())
                    .addKeyValue("partition", record.partition())
                    .addKeyValue("offset", record.offset())
                    .setCause(ex)
                    .log("Kafka record sent to dead-letter topic {}.DLT", record.topic());
        }

        @Override
        public void recoveryFailed(ConsumerRecord<?, ?> record, Exception original, Exception failure) {
            log.atError()
                    .addKeyValue("topic", record.topic())
                    .addKeyValue("partition", record.partition())
                    .addKeyValue("offset", record.offset())
                    .addKeyValue("originalError", original.getClass().getSimpleName())
                    .setCause(failure)
                    .log("Publishing Kafka record to the dead-letter topic failed; it will be redelivered");
        }
    }
}
