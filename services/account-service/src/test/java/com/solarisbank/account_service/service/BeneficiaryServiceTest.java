package com.solarisbank.account_service.service;

import com.solarisbank.account_service.dto.BeneficiaryRequest;
import com.solarisbank.account_service.dto.BeneficiaryResponse;
import com.solarisbank.account_service.exception.BusinessException;
import com.solarisbank.account_service.model.Beneficiary;
import com.solarisbank.account_service.repository.BeneficiaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BeneficiaryServiceTest {

    @Mock
    private BeneficiaryRepository beneficiaryRepository;

    @InjectMocks
    private BeneficiaryService beneficiaryService;

    private UUID userId;
    private UUID beneficiaryId;
    private Beneficiary savedBeneficiary;

    private static final String IBAN = "FR7630006000011234567890189";

    @BeforeEach
    void setUp() {
        userId        = UUID.randomUUID();
        beneficiaryId = UUID.randomUUID();

        savedBeneficiary = Beneficiary.builder()
                .id(beneficiaryId)
                .userId(userId)
                .name("Papa")
                .iban(IBAN)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── getAll ─────────────────────────────────────────────────────────────────

    @Test
    void getAll_shouldReturnBeneficiaryList() {
        when(beneficiaryRepository.findByUserIdOrderByNameAsc(userId))
                .thenReturn(List.of(savedBeneficiary));

        List<BeneficiaryResponse> result = beneficiaryService.getAll(userId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(beneficiaryId);
        assertThat(result.getFirst().getName()).isEqualTo("Papa");
        assertThat(result.getFirst().getIban()).isEqualTo(IBAN);
    }

    @Test
    void getAll_shouldReturnEmptyList_whenNoBeneficiaries() {
        when(beneficiaryRepository.findByUserIdOrderByNameAsc(userId))
                .thenReturn(List.of());

        List<BeneficiaryResponse> result = beneficiaryService.getAll(userId);

        assertThat(result).isEmpty();
    }

    // ── add ────────────────────────────────────────────────────────────────────

    @Test
    void add_shouldSaveAndReturnBeneficiary() {
        BeneficiaryRequest req = new BeneficiaryRequest();
        req.setName("Papa");
        req.setIban(IBAN);

        when(beneficiaryRepository.existsByUserIdAndIban(userId, IBAN)).thenReturn(false);
        when(beneficiaryRepository.save(any(Beneficiary.class))).thenReturn(savedBeneficiary);

        BeneficiaryResponse response = beneficiaryService.add(userId, req);

        assertThat(response.getId()).isEqualTo(beneficiaryId);
        assertThat(response.getName()).isEqualTo("Papa");
        assertThat(response.getIban()).isEqualTo(IBAN);
        verify(beneficiaryRepository).save(argThat(b ->
                b.getUserId().equals(userId) && b.getName().equals("Papa") && b.getIban().equals(IBAN)));
    }

    @Test
    void add_shouldThrowConflict_whenIbanAlreadyExists() {
        BeneficiaryRequest req = new BeneficiaryRequest();
        req.setName("Papa");
        req.setIban(IBAN);

        when(beneficiaryRepository.existsByUserIdAndIban(userId, IBAN)).thenReturn(true);

        assertThatThrownBy(() -> beneficiaryService.add(userId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already in your beneficiaries");

        verify(beneficiaryRepository, never()).save(any());
    }

    // ── delete ─────────────────────────────────────────────────────────────────

    @Test
    void delete_shouldDeleteBeneficiary_whenOwner() {
        when(beneficiaryRepository.findById(beneficiaryId))
                .thenReturn(Optional.of(savedBeneficiary));

        beneficiaryService.delete(userId, beneficiaryId);

        verify(beneficiaryRepository).delete(savedBeneficiary);
    }

    @Test
    void delete_shouldThrowNotFound_whenBeneficiaryDoesNotExist() {
        when(beneficiaryRepository.findById(beneficiaryId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> beneficiaryService.delete(userId, beneficiaryId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");

        verify(beneficiaryRepository, never()).delete(any());
    }

    @Test
    void delete_shouldThrowForbidden_whenUserIsNotOwner() {
        when(beneficiaryRepository.findById(beneficiaryId))
                .thenReturn(Optional.of(savedBeneficiary));

        UUID otherUser = UUID.randomUUID();

        assertThatThrownBy(() -> beneficiaryService.delete(otherUser, beneficiaryId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Access denied");

        verify(beneficiaryRepository, never()).delete(any());
    }
}
