package com.junaid.loan.core;

import com.junaid.loan.core.RiskAssessment.Flag;
import com.junaid.loan.core.RiskAssessment.Level;
import com.junaid.loan.core.RiskAssessment.Severity;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Applies simple, explainable underwriting checks. Thresholds follow common US lending rules of thumb
 * (43% DTI, 80% LTV for mortgage insurance, 620 minimum credit score). They're a screening aid, not a lending decision.
 */
public final class RiskAnalyzer {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public RiskAssessment assess(LoanFields merged, List<LoanFields> perDocument, List<BankStatementAnalyzer.Deposit> deposits) {
        Objects.requireNonNull(merged, "merged fields are required");
        List<Flag> flags = new ArrayList<>();
        BigDecimal monthlyIncome = merged.effectiveMonthlyIncome();
        BigDecimal payment = estimatedPayment(merged);

        if (monthlyIncome == null || monthlyIncome.signum() <= 0) {
            flags.add(new Flag("MISSING_INCOME", Severity.HIGH, "No income could be found in the documents"));
        }

        BigDecimal dti = null;
        if (monthlyIncome != null && monthlyIncome.signum() > 0 && (merged.monthlyDebtPayments() != null || payment != null)) {
            BigDecimal obligations = nz(merged.monthlyDebtPayments()).add(nz(payment));
            dti = percent(obligations, monthlyIncome);
            String basis = payment != null ? "existing debts plus the estimated new payment" : "existing debts only";
            if (dti.compareTo(BigDecimal.valueOf(43)) > 0) {
                flags.add(new Flag("HIGH_DTI", Severity.HIGH, "Debt-to-income is " + dti + "% (" + basis + "), above the 43% guideline"));
            } else if (dti.compareTo(BigDecimal.valueOf(36)) > 0) {
                flags.add(new Flag("ELEVATED_DTI", Severity.MEDIUM, "Debt-to-income is " + dti + "% (" + basis + "), above the preferred 36%"));
            }
        }

        BigDecimal ltv = null;
        if (merged.loanAmount() != null && merged.propertyValue() != null && merged.propertyValue().signum() > 0) {
            ltv = percent(merged.loanAmount(), merged.propertyValue());
            if (ltv.compareTo(BigDecimal.valueOf(95)) > 0) {
                flags.add(new Flag("HIGH_LTV", Severity.HIGH, "Loan-to-value is " + ltv + "%, above 95%"));
            } else if (ltv.compareTo(BigDecimal.valueOf(80)) > 0) {
                flags.add(new Flag("LTV_ABOVE_80", Severity.MEDIUM, "Loan-to-value is " + ltv + "%, so mortgage insurance is likely required"));
            }
        }

        Integer score = merged.creditScore();
        if (score != null) {
            if (score < 620) {
                flags.add(new Flag("LOW_CREDIT_SCORE", Severity.HIGH, "Credit score " + score + " is below 620"));
            } else if (score < 680) {
                flags.add(new Flag("FAIR_CREDIT_SCORE", Severity.MEDIUM, "Credit score " + score + " is in the fair range (620-679)"));
            }
        }

        checkIncomeConsistency(perDocument, flags);

        if (monthlyIncome != null && monthlyIncome.signum() > 0 && deposits != null) {
            BigDecimal threshold = monthlyIncome.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            for (BankStatementAnalyzer.Deposit d : deposits) {
                if (!d.payroll() && d.amount().compareTo(threshold) >= 0) {
                    flags.add(new Flag("LARGE_DEPOSIT", Severity.MEDIUM,
                            "Non-payroll deposit of $" + d.amount().toPlainString() + " on " + d.date() + " (" + d.description()
                                    + ") is at least half a month's income; ask for the source"));
                }
            }
        }

        List<String> keyMissing = new ArrayList<>();
        if (merged.loanAmount() == null) keyMissing.add("loan amount");
        if (merged.propertyValue() == null) keyMissing.add("property value");
        if (merged.creditScore() == null) keyMissing.add("credit score");
        if (merged.monthlyDebtPayments() == null) keyMissing.add("monthly debts");
        if (!keyMissing.isEmpty()) {
            flags.add(new Flag("MISSING_DATA", Severity.INFO, "Not found in the documents: " + String.join(", ", keyMissing)));
        }

        return new RiskAssessment(levelOf(flags), dti, ltv, payment, flags);
    }

    /** Standard amortised payment: P * r / (1 - (1 + r)^-n). */
    static BigDecimal estimatedPayment(LoanFields f) {
        if (f.loanAmount() == null || f.interestRate() == null || f.loanTermMonths() == null || f.loanTermMonths() <= 0) {
            return null;
        }
        double principal = f.loanAmount().doubleValue();
        int n = f.loanTermMonths();
        double r = f.interestRate().doubleValue() / 100 / 12;
        double payment = r == 0 ? principal / n : principal * r / (1 - Math.pow(1 + r, -n));
        return BigDecimal.valueOf(payment).setScale(2, RoundingMode.HALF_UP);
    }

    private static void checkIncomeConsistency(List<LoanFields> perDocument, List<Flag> flags) {
        if (perDocument == null) {
            return;
        }
        BigDecimal min = null;
        BigDecimal max = null;
        for (LoanFields f : perDocument) {
            BigDecimal m = f.effectiveMonthlyIncome();
            if (m == null || m.signum() <= 0) {
                continue;
            }
            min = min == null || m.compareTo(min) < 0 ? m : min;
            max = max == null || m.compareTo(max) > 0 ? m : max;
        }
        if (min != null && max.divide(min, MathContext.DECIMAL64).compareTo(new BigDecimal("1.10")) > 0) {
            flags.add(new Flag("INCOME_MISMATCH", Severity.HIGH,
                    "Monthly income differs across documents: $" + min.toPlainString() + " vs $" + max.toPlainString() + " (more than 10%)"));
        }
    }

    private static Level levelOf(List<Flag> flags) {
        boolean high = flags.stream().anyMatch(f -> f.severity() == Severity.HIGH);
        boolean medium = flags.stream().anyMatch(f -> f.severity() == Severity.MEDIUM);
        return high ? Level.HIGH : medium ? Level.MEDIUM : Level.LOW;
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
