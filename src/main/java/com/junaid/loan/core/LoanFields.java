package com.junaid.loan.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Key facts pulled from loan documents. Any field may be null when a document doesn't mention it. */
public record LoanFields(
        String applicantName,
        String employer,
        BigDecimal annualIncome,
        BigDecimal monthlyIncome,
        BigDecimal monthlyDebtPayments,
        BigDecimal loanAmount,
        BigDecimal propertyValue,
        Integer creditScore,
        BigDecimal interestRate,
        Integer loanTermMonths) {

    public static LoanFields empty() {
        return new LoanFields(null, null, null, null, null, null, null, null, null, null);
    }

    /** Keeps this record's values and fills any gaps from {@code other}. */
    public LoanFields fillFrom(LoanFields other) {
        return new LoanFields(
                applicantName != null ? applicantName : other.applicantName,
                employer != null ? employer : other.employer,
                annualIncome != null ? annualIncome : other.annualIncome,
                monthlyIncome != null ? monthlyIncome : other.monthlyIncome,
                monthlyDebtPayments != null ? monthlyDebtPayments : other.monthlyDebtPayments,
                loanAmount != null ? loanAmount : other.loanAmount,
                propertyValue != null ? propertyValue : other.propertyValue,
                creditScore != null ? creditScore : other.creditScore,
                interestRate != null ? interestRate : other.interestRate,
                loanTermMonths != null ? loanTermMonths : other.loanTermMonths);
    }

    /** Monthly gross income, from the monthly figure if present, otherwise annual / 12. */
    public BigDecimal effectiveMonthlyIncome() {
        if (monthlyIncome != null) {
            return monthlyIncome;
        }
        return annualIncome == null ? null : annualIncome.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    /** Field name to value, in a stable order. Handy for evaluation and display. */
    public Map<String, Object> asMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("applicantName", applicantName);
        m.put("employer", employer);
        m.put("annualIncome", annualIncome);
        m.put("monthlyIncome", monthlyIncome);
        m.put("monthlyDebtPayments", monthlyDebtPayments);
        m.put("loanAmount", loanAmount);
        m.put("propertyValue", propertyValue);
        m.put("creditScore", creditScore);
        m.put("interestRate", interestRate);
        m.put("loanTermMonths", loanTermMonths);
        return m;
    }

    public List<String> missingFields() {
        List<String> missing = new ArrayList<>();
        asMap().forEach((k, v) -> {
            if (v == null) {
                missing.add(k);
            }
        });
        return missing;
    }
}
