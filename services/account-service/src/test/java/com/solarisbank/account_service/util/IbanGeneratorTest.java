package com.solarisbank.account_service.util;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IbanGeneratorTest {

    private final IbanGenerator ibanGenerator = new IbanGenerator();

    @Test
    void generate_shouldStartWithFR() {
        assertThat(ibanGenerator.generate()).startsWith("FR");
    }

    @Test
    void generate_shouldStartWithFR76() {
        for (int i = 0; i < 10; i++) {
            assertThat(ibanGenerator.generate()).startsWith("FR76");
        }
    }

    @Test
    void generate_shouldHaveLength27() {
        // FR(2) + check(2) + BANK(5) + BRANCH(5) + account(11) + RIB_KEY(2) = 27
        assertThat(ibanGenerator.generate()).hasSize(27);
    }

    @Test
    void generate_shouldProduceValidIban_mod97EqualsOne() {
        String iban = ibanGenerator.generate();
        assertThat(mod97(iban)).isEqualTo(1);
    }

    @Test
    void generate_shouldProduceDifferentIbans_onSuccessiveCalls() {
        Set<String> ibans = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            ibans.add(ibanGenerator.generate());
        }
        // Avec 20 appels, on doit obtenir au moins 2 IBANs différents
        assertThat(ibans.size()).isGreaterThan(1);
    }

    @Test
    void generate_shouldContainKnownBankCode() {
        // Bank code 30006 commence à la position 4 (après FR + 2 check digits)
        String iban = ibanGenerator.generate();
        assertThat(iban.substring(4, 9)).isEqualTo("30006");
    }

    @Test
    void generate_shouldContainKnownBranchCode() {
        // Branch code 00001 commence à la position 9
        String iban = ibanGenerator.generate();
        assertThat(iban.substring(9, 14)).isEqualTo("00001");
    }

    @Test
    void generate_shouldContainValidRibKey() {
        // Clé RIB sur 2 chiffres en position 25-26 (après n° de compte)
        String iban = ibanGenerator.generate();
        String ribKey = iban.substring(25, 27);
        assertThat(ribKey).matches("\\d{2}");
        int key = Integer.parseInt(ribKey);
        assertThat(key).isBetween(1, 97);
    }

    /**
     * Algorithme standard de validation IBAN (MOD-97).
     * Un IBAN valide doit donner un reste de 1.
     */
    private int mod97(String iban) {
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder numeric = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            if (Character.isLetter(c)) {
                numeric.append(c - 'A' + 10);
            } else {
                numeric.append(c);
            }
        }
        return new BigInteger(numeric.toString()).mod(BigInteger.valueOf(97)).intValue();
    }
}
