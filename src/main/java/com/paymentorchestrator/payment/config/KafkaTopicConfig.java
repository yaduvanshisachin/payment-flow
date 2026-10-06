package com.paymentorchestrator.payment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares topics explicitly instead of relying on the broker's
 * auto.create.topics.enable default. Spring Boot's KafkaAdmin picks up
 * NewTopic beans and creates them on startup if missing - this is also
 * where future weeks add payment.psp.result, payment.webhook.pending, etc.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic paymentCreatedTopic() {
        return TopicBuilder.name("payment.created")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
