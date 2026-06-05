package com.solarisbank.notification_service.kafka.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    // Topics consumed by notification-service (published by transaction-service)
    public static final String TOPIC_TRANSACTION_COMPLETED = "transaction.completed";
    public static final String TOPIC_TRANSACTION_FAILED    = "transaction.failed";

    @Bean
    public NewTopic transactionCompletedTopic() {
        return TopicBuilder.name(TOPIC_TRANSACTION_COMPLETED).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic transactionFailedTopic() {
        return TopicBuilder.name(TOPIC_TRANSACTION_FAILED).partitions(1).replicas(1).build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
