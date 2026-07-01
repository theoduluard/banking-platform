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

    // RIB letter-to-digit mapping: A/J→1, B/K/S→2, C/L/T→3, D/M/U→4, E/N/V→5,
    //                              F/O/W→6, G/P/X→7, H/Q/Y→8, I/R/Z→9
    private static final int[] RIB_LETTER_MAP = {
        1,2,3,4,5,6,7,8,9,  // A–I
        1,2,3,4,5,6,7,8,9,  // J–R
        2,3,4,5,6,7,8,9     // S–Z
    };

    public String generate() {
        String iban;
        do {
            String accountNumber = String.format("%011d", new Random().nextLong(99_999_999_999L));
            String ribKey = computeRibKey(BANK_CODE, BRANCH_CODE, accountNumber);
            String bban   = BANK_CODE + BRANCH_CODE + accountNumber + ribKey;
            iban = COUNTRY_CODE + computeCheckDigits(bban) + bban;
        } while (!iban.startsWith("FR76"));
        return iban;
    }

    // Clé RIB : 97 - ((89 × banque + 15 × guichet + 3 × compte) mod 97)
    private String computeRibKey(String bankCode, String branchCode, String accountNumber) {
        long bank    = Long.parseLong(convertRibChars(bankCode));
        long branch  = Long.parseLong(convertRibChars(branchCode));
        long account = Long.parseLong(convertRibChars(accountNumber));
        long key = 97 - ((89 * bank + 15 * branch + 3 * account) % 97);
        return String.format("%02d", key);
    }

    private String convertRibChars(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toUpperCase().toCharArray()) {
            if (Character.isLetter(c)) {
                sb.append(RIB_LETTER_MAP[c - 'A']);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String computeCheckDigits(String bban) {
        String rearranged = bban + COUNTRY_CODE + "00";
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
