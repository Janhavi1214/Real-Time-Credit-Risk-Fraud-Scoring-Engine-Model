package com.riskguard.ingestion.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic definitions.  Spring's {@code KafkaAdmin} auto-creates these
 * topics on startup when a broker is reachable.  In production the topics
 * would be managed externally (Terraform / scripts), but for a demo this
 * ensures the topic exists even when running outside Docker Compose.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic transactionsRawTopic() {
        return TopicBuilder.name("transactions.raw")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
