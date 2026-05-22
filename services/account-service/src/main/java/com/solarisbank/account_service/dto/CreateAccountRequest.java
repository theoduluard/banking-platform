package com.solarisbank.account_service.dto;

import com.solarisbank.account_service.model.Account;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateAccountRequest {

    @NotNull
    private Account.Type type;

}
