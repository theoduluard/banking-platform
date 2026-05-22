package com.solarisbank.account_service.service;

import com.solarisbank.account_service.dto.AccountResponse;
import com.solarisbank.account_service.dto.CreateAccountRequest;
import com.solarisbank.account_service.exception.BusinessException;
import com.solarisbank.account_service.model.Account;
import com.solarisbank.account_service.repository.AccountRepository;
import com.solarisbank.account_service.util.IbanGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final IbanGenerator ibanGenerator;

    public AccountResponse create(UUID userId, CreateAccountRequest request) {
        String iban;
        do {
            iban = ibanGenerator.generate();
        } while (accountRepository.existsByIban(iban));

        Account account = Account.builder()
                .userId(userId)
                .iban(iban)
                .type(request.getType())
                .build();

        return toResponse(accountRepository.save(account));
    }

    public List<AccountResponse> getMyAccounts(UUID userId) {
        return accountRepository.findByUserId(userId)
                .stream().map(this::toResponse).toList();
    }

    public AccountResponse getAccount(UUID accountId, UUID userId) {
        return accountRepository.findByAccountIdAndUserId(accountId, userId)
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException("Account not found", HttpStatus.NOT_FOUND));
    }

    public AccountResponse updateStatus(UUID accountId, UUID userId, Account.Status newStatus) {
        Account account = accountRepository.findByAccountIdAndUserId(accountId, userId)
                .orElseThrow(() -> new BusinessException("Account not found", HttpStatus.NOT_FOUND));

        account.setStatus(newStatus);
        return toResponse(accountRepository.save(account));
    }

    private AccountResponse toResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getAccountId())
                .iban(account.getIban())
                .type(account.getType())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .status(account.getStatus())
                .createdAt(account.getCreatedAt())
                .build();
    }
}