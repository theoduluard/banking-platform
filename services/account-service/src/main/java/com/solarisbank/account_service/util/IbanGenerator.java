package com.solarisbank.account_service.util;

import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Random;
import java.util.stream.Collectors;

@Component
public class IbanGenerator {

    private static final String COUNTRY_CODE = "FR";
    private static final String BANK_CODE    = "30006";
    private static final String BRANCH_CODE  = "00001";

    public String generate() {
        String accountNumber = String.format("%011d", new Random().nextLong(99999999999L));
        String bban = BANK_CODE + BRANCH_CODE + accountNumber;
        String checkDigits = computeCheckDigits(bban);
        return COUNTRY_CODE + checkDigits + bban;
    }

    private String computeCheckDigits(String bban) {
        String rearranged = bban + IbanGenerator.COUNTRY_CODE + "00";
        String numeric    = rearranged.chars()
                .mapToObj(c -> Character.isLetter(c)
                        ? String.valueOf(c - 'A' + 10)
                        : String.valueOf((char) c))
                .collect(Collectors.joining());

        int remainder = new BigInteger(numeric).mod(BigInteger.valueOf(97)).intValue();
        int checkDigit = 98 - remainder;
        return String.format("%02d", checkDigit);
    }
}
