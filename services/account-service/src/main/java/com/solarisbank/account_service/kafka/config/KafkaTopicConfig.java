package com.solarisbank.account_service.kafka.config;

public class KafkaTopicConfig {

    // Topics consommés par account-service (publiés par transaction-service)
    public static final String TOPIC_DEBIT_REQUESTED  = "account.debit.requested";
    public static final String TOPIC_CREDIT_REQUESTED = "account.credit.requested";

    // Topics publiés par account-service
    public static final String TOPIC_DEBIT_RESULT  = "account.debit.result";
    public static final String TOPIC_CREDIT_RESULT = "account.credit.result";

    // Approval workflow events (consumed by messaging-service)
    public static final String TOPIC_ACCOUNT_APPROVED = "account.approved";
    public static final String TOPIC_ACCOUNT_REJECTED = "account.rejected";
}
