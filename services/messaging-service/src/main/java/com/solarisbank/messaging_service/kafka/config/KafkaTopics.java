package com.solarisbank.messaging_service.kafka.config;

public class KafkaTopics {
    // Published by account-service, consumed here
    public static final String ACCOUNT_APPROVED = "account.approved";
    public static final String ACCOUNT_REJECTED = "account.rejected";
}
