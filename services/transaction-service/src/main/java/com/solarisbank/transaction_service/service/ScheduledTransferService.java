package com.solarisbank.transaction_service.service;

import com.solarisbank.transaction_service.client.AccountClient;
import com.solarisbank.transaction_service.client.dto.AccountResponse;
import com.solarisbank.transaction_service.dto.ScheduledTransferRequest;
import com.solarisbank.transaction_service.dto.ScheduledTransferResponse;
import com.solarisbank.transaction_service.exception.BusinessException;
import com.solarisbank.transaction_service.model.ScheduledTransfer;
import com.solarisbank.transaction_service.repository.ScheduledTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledTransferService {

    private final ScheduledTransferRepository scheduledTransferRepository;
    private final AccountClient               accountClient;

    // ── Create ─────────────────────────────────────────────────────────────────

    @Transactional
    public ScheduledTransferResponse create(UUID userId, ScheduledTransferRequest request) {

        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new BusinessException("Source and destination accounts must differ",
                    HttpStatus.BAD_REQUEST);
        }

        // Validate source account ownership + active status
        AccountResponse source = accountClient.getAccount(request.getFromAccountId(), userId);
        if (!"ACTIVE".equals(source.getStatus())) {
            throw new BusinessException("Source account is not active", HttpStatus.METHOD_NOT_ALLOWED);
        }

        ScheduledTransfer transfer = scheduledTransferRepository.save(
                ScheduledTransfer.builder()
                        .fromAccountId(request.getFromAccountId())
                        .toAccountId(request.getToAccountId())
                        .initiatedByUserId(userId)
                        .amount(request.getAmount())
                        .description(request.getDescription())
                        .frequency(request.getFrequency())
                        .nextExecutionDate(request.getFirstExecutionDate())
                        .build()
        );

        log.info("[Scheduled] Created scheduled transfer {} for user {} — {} {} {}",
                transfer.getId(), userId, request.getAmount(),
                request.getFrequency(), request.getFirstExecutionDate());

        return toResponse(transfer);
    }

    // ── List ───────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ScheduledTransferResponse> getMyScheduledTransfers(UUID userId) {
        return scheduledTransferRepository
                .findByInitiatedByUserIdAndActiveTrue(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Cancel ─────────────────────────────────────────────────────────────────

    @Transactional
    public void cancel(UUID transferId, UUID userId) {
        ScheduledTransfer transfer = scheduledTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("Scheduled transfer not found",
                        HttpStatus.NOT_FOUND));

        if (!transfer.getInitiatedByUserId().equals(userId)) {
            throw new BusinessException("Access denied", HttpStatus.FORBIDDEN);
        }
        if (!transfer.isActive()) {
            throw new BusinessException("Scheduled transfer is already cancelled",
                    HttpStatus.BAD_REQUEST);
        }

        transfer.setActive(false);
        scheduledTransferRepository.save(transfer);
        log.info("[Scheduled] Transfer {} cancelled by user {}", transferId, userId);
    }

    // ── Scheduler ──────────────────────────────────────────────────────────────

    /**
     * Runs daily at 09:00 UTC and processes all due scheduled transfers.
     * Execution is synchronous (direct debit + credit) — no Kafka saga.
     * Each transfer is handled in its own transaction so a single failure
     * doesn't roll back the other transfers.
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void executeScheduledTransfers() {
        List<ScheduledTransfer> due =
                scheduledTransferRepository
                        .findByActiveTrueAndNextExecutionDateLessThanEqual(LocalDate.now());

        log.info("[Scheduler] Processing {} due scheduled transfers", due.size());

        for (ScheduledTransfer transfer : due) {
            executeOne(transfer);
        }
    }

    @Transactional
    public void executeOne(ScheduledTransfer transfer) {
        try {
            accountClient.debit(transfer.getFromAccountId(),
                    transfer.getInitiatedByUserId(), transfer.getAmount());
            accountClient.credit(transfer.getToAccountId(), transfer.getAmount());

            // Advance to the next execution date
            transfer.setNextExecutionDate(nextDate(transfer));
            scheduledTransferRepository.save(transfer);

            log.info("[Scheduler] Executed scheduled transfer {} ({}  {})",
                    transfer.getId(), transfer.getAmount(), transfer.getCurrency());

        } catch (Exception e) {
            // Log and continue — a single failure must not stop the others
            log.error("[Scheduler] Failed to execute scheduled transfer {}: {}",
                    transfer.getId(), e.getMessage());
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private LocalDate nextDate(ScheduledTransfer transfer) {
        return switch (transfer.getFrequency()) {
            case WEEKLY  -> transfer.getNextExecutionDate().plusWeeks(1);
            case MONTHLY -> transfer.getNextExecutionDate().plusMonths(1);
        };
    }

    private ScheduledTransferResponse toResponse(ScheduledTransfer t) {
        return ScheduledTransferResponse.builder()
                .id(t.getId())
                .fromAccountId(t.getFromAccountId())
                .toAccountId(t.getToAccountId())
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .description(t.getDescription())
                .frequency(t.getFrequency())
                .nextExecutionDate(t.getNextExecutionDate())
                .active(t.isActive())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
