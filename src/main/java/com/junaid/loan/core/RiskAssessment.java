package com.junaid.loan.core;

import java.math.BigDecimal;
import java.util.List;

/**
 * Outcome of the risk rules.
 *
 * @param dtiPercent              debt-to-income including the estimated new payment when it can be computed; null if income is unknown
 * @param ltvPercent              loan-to-value; null if loan amount or property value is unknown
 * @param estimatedMonthlyPayment principal and interest on the new loan; null when rate or term is unknown
 */
public record RiskAssessment(
        Level level,
        BigDecimal dtiPercent,
        BigDecimal ltvPercent,
        BigDecimal estimatedMonthlyPayment,
        List<Flag> flags) {

    public enum Level { LOW, MEDIUM, HIGH }

    public enum Severity { INFO, MEDIUM, HIGH }

    public record Flag(String code, Severity severity, String message) {}

    public RiskAssessment {
        flags = List.copyOf(flags);
    }

    public boolean hasFlag(String code) {
        return flags.stream().anyMatch(f -> f.code().equals(code));
    }
}
