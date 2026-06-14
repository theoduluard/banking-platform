package com.solarisbank.transaction_service.service;

import com.solarisbank.transaction_service.client.AccountClient;
import com.solarisbank.transaction_service.client.dto.AccountResponse;
import com.solarisbank.transaction_service.dto.ScheduledTransferRequest;
import com.solarisbank.transaction_service.dto.ScheduledTransferResponse;
import com.solarisbank.transaction_service.exception.BusinessException;
import com.solarisbank.transaction_service.model.ScheduledTransfer;
import com.solarisbank.transaction_service.model.Transaction;
import com.solarisbank.transaction_service.repository.ScheduledTransferRepository;
import com.solarisbank.transaction_service.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledTransferServiceTest {

    @Mock private ScheduledTransferRepository scheduledTransferRepository;
    @Mock private TransactionRepository       transactionRepository;
    @Mock private AccountClient               accountClient;

    @InjectMocks private ScheduledTransferService scheduledTransferService;

    private UUID userId;
    private UUID fromAccountId;
    private UUID toAccountId;
    private UUID transferId;
    private AccountResponse activeAccount;
    private ScheduledTransfer savedTransfer;

    @BeforeEach
    void setUp() {
        // @Lazy @Autowired self-injection is not handled by @InjectMocks —
        // inject it manually so executeScheduledTransfers() can call self.executeOne().
        ReflectionTestUtils.setField(scheduledTransferService, "self", scheduledTransferService);

        userId        = UUID.randomUUID();
        fromAccountId = UUID.randomUUID();
        toAccountId   = UUID.randomUUID();
        transferId    = UUID.randomUUID();

        activeAccount = new AccountResponse();
        activeAccount.setId(fromAccountId);
        activeAccount.setStatus("ACTIVE");
        activeAccount.setBalance(new BigDecimal("1000.00"));
        activeAccount.setUserId(userId);

        savedTransfer = ScheduledTransfer.builder()
                .id(transferId)
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .initiatedByUserId(userId)
                .amount(new BigDecimal("150.00"))
                .currency("EUR")
                .description("Loyer")
                .frequency(ScheduledTransfer.Frequency.MONTHLY)
                .nextExecutionDate(LocalDate.now().plusDays(5))
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── create ─────────────────────────────────────────────────────────────────

    @Test
    void create_shouldSaveAndReturnResponse_whenValid() {
        ScheduledTransferRequest req = buildRequest();

        when(accountClient.getAccount(fromAccountId, userId)).thenReturn(activeAccount);
        when(scheduledTransferRepository.save(any(ScheduledTransfer.class))).thenReturn(savedTransfer);

        ScheduledTransferResponse response = scheduledTransferService.create(userId, req);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(transferId);
        assertThat(response.getFrequency()).isEqualTo(ScheduledTransfer.Frequency.MONTHLY);
        verify(scheduledTransferRepository).save(any(ScheduledTransfer.class));
    }

    @Test
    void create_shouldThrowBadRequest_whenSameAccounts() {
        ScheduledTransferRequest req = buildRequest();
        req.setToAccountId(fromAccountId);   // same as from

        assertThatThrownBy(() -> scheduledTransferService.create(userId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("differ");

        verifyNoInteractions(accountClient, scheduledTransferRepository);
    }

    @Test
    void create_shouldThrowMethodNotAllowed_whenSourceAccountNotActive() {
        activeAccount.setStatus("BLOCKED");
        when(accountClient.getAccount(fromAccountId, userId)).thenReturn(activeAccount);

        ScheduledTransferRequest req = buildRequest();

        assertThatThrownBy(() -> scheduledTransferService.create(userId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not active");

        verify(scheduledTransferRepository, never()).save(any());
    }

    @Test
    void create_shouldWorkWithWeeklyFrequency() {
        ScheduledTransferRequest req = buildRequest();
        req.setFrequency(ScheduledTransfer.Frequency.WEEKLY);

        ScheduledTransfer weeklyTransfer = ScheduledTransfer.builder()
                .id(transferId).fromAccountId(fromAccountId).toAccountId(toAccountId)
                .initiatedByUserId(userId).amount(new BigDecimal("150.00")).currency("EUR")
                .frequency(ScheduledTransfer.Frequency.WEEKLY)
                .nextExecutionDate(LocalDate.now().plusDays(1))
                .active(true).createdAt(LocalDateTime.now()).build();

        when(accountClient.getAccount(fromAccountId, userId)).thenReturn(activeAccount);
        when(scheduledTransferRepository.save(any())).thenReturn(weeklyTransfer);

        ScheduledTransferResponse response = scheduledTransferService.create(userId, req);
        assertThat(response.getFrequency()).isEqualTo(ScheduledTransfer.Frequency.WEEKLY);
    }

    // ── getMyScheduledTransfers ────────────────────────────────────────────────

    @Test
    void getMyScheduledTransfers_shouldReturnActiveTransfers() {
        when(scheduledTransferRepository.findByInitiatedByUserIdAndActiveTrue(userId))
                .thenReturn(List.of(savedTransfer));

        List<ScheduledTransferResponse> result =
                scheduledTransferService.getMyScheduledTransfers(userId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(transferId);
    }

    @Test
    void getMyScheduledTransfers_shouldReturnEmpty_whenNoneActive() {
        when(scheduledTransferRepository.findByInitiatedByUserIdAndActiveTrue(userId))
                .thenReturn(List.of());

        List<ScheduledTransferResponse> result =
                scheduledTransferService.getMyScheduledTransfers(userId);

        assertThat(result).isEmpty();
    }

    // ── cancel ─────────────────────────────────────────────────────────────────

    @Test
    void cancel_shouldDeactivateTransfer_whenOwnerRequests() {
        when(scheduledTransferRepository.findById(transferId))
                .thenReturn(Optional.of(savedTransfer));
        when(scheduledTransferRepository.save(any())).thenReturn(savedTransfer);

        scheduledTransferService.cancel(transferId, userId);

        verify(scheduledTransferRepository).save(argThat(t -> !t.isActive()));
    }

    @Test
    void cancel_shouldThrowNotFound_whenTransferDoesNotExist() {
        when(scheduledTransferRepository.findById(transferId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduledTransferService.cancel(transferId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void cancel_shouldThrowForbidden_whenUserIsNotOwner() {
        when(scheduledTransferRepository.findById(transferId))
                .thenReturn(Optional.of(savedTransfer));

        assertThatThrownBy(() -> scheduledTransferService.cancel(transferId, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Access denied");

        verify(scheduledTransferRepository, never()).save(any());
    }

    @Test
    void cancel_shouldThrowBadRequest_whenAlreadyCancelled() {
        savedTransfer.setActive(false);
        when(scheduledTransferRepository.findById(transferId))
                .thenReturn(Optional.of(savedTransfer));

        assertThatThrownBy(() -> scheduledTransferService.cancel(transferId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already cancelled");
    }

    // ── executeOne ─────────────────────────────────────────────────────────────

    @Test
    void executeOne_shouldDebitAndCreditAndAdvanceDate_whenMonthly() {
        doNothing().when(accountClient).debit(fromAccountId, userId, savedTransfer.getAmount());
        doNothing().when(accountClient).credit(toAccountId, savedTransfer.getAmount());
        when(scheduledTransferRepository.save(any())).thenReturn(savedTransfer);

        LocalDate originalDate = savedTransfer.getNextExecutionDate();
        scheduledTransferService.executeOne(savedTransfer);

        verify(accountClient).debit(fromAccountId, userId, savedTransfer.getAmount());
        verify(accountClient).credit(toAccountId, savedTransfer.getAmount());
        verify(scheduledTransferRepository).save(argThat(t ->
                t.getNextExecutionDate().isAfter(originalDate)));
    }

    @Test
    void executeOne_shouldAdvanceByOneWeek_whenWeeklyFrequency() {
        savedTransfer.setFrequency(ScheduledTransfer.Frequency.WEEKLY);
        LocalDate originalDate = savedTransfer.getNextExecutionDate();

        doNothing().when(accountClient).debit(any(), any(), any());
        doNothing().when(accountClient).credit(any(), any());
        when(scheduledTransferRepository.save(any())).thenReturn(savedTransfer);

        scheduledTransferService.executeOne(savedTransfer);

        verify(scheduledTransferRepository).save(argThat(t ->
                t.getNextExecutionDate().equals(originalDate.plusWeeks(1))));
    }

    @Test
    void executeOne_shouldAdvanceByOneMonth_whenMonthlyFrequency() {
        savedTransfer.setFrequency(ScheduledTransfer.Frequency.MONTHLY);
        LocalDate originalDate = savedTransfer.getNextExecutionDate();

        doNothing().when(accountClient).debit(any(), any(), any());
        doNothing().when(accountClient).credit(any(), any());
        when(scheduledTransferRepository.save(any())).thenReturn(savedTransfer);

        scheduledTransferService.executeOne(savedTransfer);

        verify(scheduledTransferRepository).save(argThat(t ->
                t.getNextExecutionDate().equals(originalDate.plusMonths(1))));
    }

    @Test
    void executeOne_shouldNotThrow_whenDebitFails() {
        doThrow(new BusinessException("Insufficient funds", HttpStatus.METHOD_NOT_ALLOWED))
                .when(accountClient).debit(fromAccountId, userId, savedTransfer.getAmount());

        // Must not propagate — failure is logged and swallowed
        scheduledTransferService.executeOne(savedTransfer);

        verify(scheduledTransferRepository, never()).save(any());
    }

    // ── executeScheduledTransfers ──────────────────────────────────────────────

    @Test
    void executeScheduledTransfers_shouldProcessAllDueTransfers() {
        when(scheduledTransferRepository.findByActiveTrueAndNextExecutionDateLessThanEqual(any()))
                .thenReturn(List.of(savedTransfer));
        doNothing().when(accountClient).debit(any(), any(), any());
        doNothing().when(accountClient).credit(any(), any());
        when(scheduledTransferRepository.save(any())).thenReturn(savedTransfer);

        scheduledTransferService.executeScheduledTransfers();

        verify(accountClient).debit(fromAccountId, userId, savedTransfer.getAmount());
        verify(accountClient).credit(toAccountId, savedTransfer.getAmount());
    }

    @Test
    void executeScheduledTransfers_shouldDoNothing_whenNoneAreDue() {
        when(scheduledTransferRepository.findByActiveTrueAndNextExecutionDateLessThanEqual(any()))
                .thenReturn(List.of());

        scheduledTransferService.executeScheduledTransfers();

        verifyNoInteractions(accountClient);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private ScheduledTransferRequest buildRequest() {
        ScheduledTransferRequest req = new ScheduledTransferRequest();
        req.setFromAccountId(fromAccountId);
        req.setToAccountId(toAccountId);
        req.setAmount(new BigDecimal("150.00"));
        req.setDescription("Loyer");
        req.setFrequency(ScheduledTransfer.Frequency.MONTHLY);
        req.setFirstExecutionDate(LocalDate.now().plusDays(5));
        return req;
    }
}
