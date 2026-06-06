package com.solarisbank.card_service.repository;

import com.solarisbank.card_service.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID> {
    List<Card> findByUserIdAndStatusNot(UUID userId, Card.CardStatus status);
    List<Card> findByAccountId(UUID accountId);
}
