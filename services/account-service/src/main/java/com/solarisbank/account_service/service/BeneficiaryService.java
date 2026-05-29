package com.solarisbank.account_service.service;

import com.solarisbank.account_service.dto.BeneficiaryRequest;
import com.solarisbank.account_service.dto.BeneficiaryResponse;
import com.solarisbank.account_service.exception.BusinessException;
import com.solarisbank.account_service.model.Beneficiary;
import com.solarisbank.account_service.repository.BeneficiaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;

    public List<BeneficiaryResponse> getAll(UUID userId) {
        return beneficiaryRepository.findByUserIdOrderByNameAsc(userId)
                .stream().map(this::toResponse).toList();
    }

    public BeneficiaryResponse add(UUID userId, BeneficiaryRequest request) {
        if (beneficiaryRepository.existsByUserIdAndIban(userId, request.getIban())) {
            throw new BusinessException(
                "This IBAN is already in your beneficiaries", HttpStatus.CONFLICT);
        }

        Beneficiary b = Beneficiary.builder()
                .userId(userId)
                .name(request.getName())
                .iban(request.getIban())
                .build();

        return toResponse(beneficiaryRepository.save(b));
    }

    public void delete(UUID userId, UUID beneficiaryId) {
        Beneficiary b = beneficiaryRepository.findById(beneficiaryId)
                .orElseThrow(() -> new BusinessException(
                    "Beneficiary not found", HttpStatus.NOT_FOUND));

        if (!b.getUserId().equals(userId)) {
            throw new BusinessException("Access denied", HttpStatus.FORBIDDEN);
        }

        beneficiaryRepository.delete(b);
    }

    private BeneficiaryResponse toResponse(Beneficiary b) {
        return BeneficiaryResponse.builder()
                .id(b.getId())
                .name(b.getName())
                .iban(b.getIban())
                .createdAt(b.getCreatedAt())
                .build();
    }
}
