package com.solarisbank.messaging_service.repository;

import com.solarisbank.messaging_service.model.SupportRequestReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SupportRequestReplyRepository extends JpaRepository<SupportRequestReply, UUID> {
    List<SupportRequestReply> findByRequestIdOrderByCreatedAtAsc(UUID requestId);
}
