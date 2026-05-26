package com.solarisbank.transaction_service.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    // Topics publiés par transaction-service
    public static final String TOPIC_DEBIT_REQUESTED  = "account.debit.requested";
    public static final String TOPIC_CREDIT_REQUESTED = "account.credit.requested";

    // Topics consommés par transaction-service (publiés par account-service)
    public static final String TOPIC_DEBIT_RESULT  = "account.debit.result";
    public static final String TOPIC_CREDIT_RESULT = "account.credit.result";

    @Bean
    public NewTopic debitRequestedTopic() {
        return TopicBuilder.name(TOPIC_DEBIT_REQUESTED).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic creditRequestedTopic() {
        return TopicBuilder.name(TOPIC_CREDIT_REQUESTED).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic debitResultTopic() {
        return TopicBuilder.name(TOPIC_DEBIT_RESULT).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic creditResultTopic() {
        return TopicBuilder.name(TOPIC_CREDIT_RESULT).partitions(1).replicas(1).build();
    }
}
