package com.junaid.loan.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class FieldExtractorTest {

    private final FieldExtractor extractor = new FieldExtractor();

    @Test
    void readsLabelledFieldsWithEvidence() {
        String text = """
                Applicant Name: Maria Lopez
                Employer: Brightline Health Systems
                Annual Gross Income: $96,000.00
                Total Monthly Debt Payments: $850.00
                Requested Loan Amount: $320,000
                Purchase Price: $400,000
                Interest Rate: 6.25%
                Loan Term: 30 years
                Credit Score: 742 (tri-merge)
                """;
        FieldExtractor.Extraction ex = extractor.extract(text);
        LoanFields f = ex.fields();

        assertEquals("Maria Lopez", f.applicantName());
        assertEquals("Brightline Health Systems", f.employer());
        assertEquals(0, new BigDecimal("96000").compareTo(f.annualIncome()));
        assertEquals(0, new BigDecimal("850").compareTo(f.monthlyDebtPayments()));
        assertEquals(0, new BigDecimal("320000").compareTo(f.loanAmount()));
        assertEquals(0, new BigDecimal("400000").compareTo(f.propertyValue()));
        assertEquals(0, new BigDecimal("6.25").compareTo(f.interestRate()));
        assertEquals(Integer.valueOf(360), f.loanTermMonths());
        assertEquals(Integer.valueOf(742), f.creditScore());
        assertEquals("Requested Loan Amount: $320,000", ex.evidence().get("loanAmount"));
    }

    @Test
    void convertsPayStubFrequencyToMonthly() {
        assertEquals(new BigDecimal("8000.01"),
                extractor.extract("Pay Frequency: Biweekly\nGross Pay: $3,692.31\n").fields().monthlyIncome());
        assertEquals(new BigDecimal("7500.00"),
                extractor.extract("Pay Frequency: Semi-monthly\nGross Pay: $3,750.00\n").fields().monthlyIncome());
        assertEquals(new BigDecimal("4333.33"),
                extractor.extract("Pay Frequency: Weekly\nGross Pay: 1000\n").fields().monthlyIncome());
    }

    @Test
    void ignoresImplausibleCreditScores() {
        assertNull(extractor.extract("Credit Score: 912").fields().creditScore());
    }

    @Test
    void doesNotConfuseAprilWithApr() {
        assertNull(extractor.extract("Signed in April 5% down").fields().interestRate());
    }

    @Test
    void termInMonthsIsKept() {
        assertEquals(Integer.valueOf(180), extractor.extract("Loan Term: 180 months").fields().loanTermMonths());
    }

    @Test
    void nameValueDoesNotSpillOntoNextLine() {
        assertNull(extractor.extract("Employer:\nAnnual Income: $50,000").fields().employer());
    }

    @Test
    void extractsSampleDocumentsAccurately() {
        // Ground truth for the fictional sample applications.
        record Case(String file, LoanFields expected) {}
        List<Case> cases = List.of(
                new Case("maria-lopez-application.txt", new LoanFields("Maria Lopez", "Brightline Health Systems",
                        new BigDecimal("96000"), null, new BigDecimal("850"), new BigDecimal("320000"),
                        new BigDecimal("400000"), 742, new BigDecimal("6.25"), 360)),
                new Case("maria-lopez-paystub.txt", new LoanFields("Maria Lopez", null, null,
                        new BigDecimal("8000.01"), null, null, null, null, null, null)),
                new Case("james-carter-application.txt", new LoanFields("James Carter", "Northgate Logistics",
                        new BigDecimal("120000"), null, new BigDecimal("2100"), new BigDecimal("380000"),
                        new BigDecimal("400000"), 655, new BigDecimal("7.1"), 360)),
                new Case("james-carter-paystub.txt", new LoanFields("James Carter", null, null,
                        new BigDecimal("7500"), null, null, null, null, null, null)),
                new Case("priya-shah-application.txt", new LoanFields("Priya Shah", "Riverside Retail Group", null,
                        new BigDecimal("6200"), new BigDecimal("3100"), new BigDecimal("25000"), null, 598, null, null)));

        for (Case c : cases) {
            LoanFields actual = extractor.extract(SampleDocs.load(c.file()).fullText()).fields();
            double accuracy = AnswerPrompts.fieldAccuracy(c.expected(), actual);
            assertEquals(1.0, accuracy, 0.0001);
        }
    }

    @Test
    void bankStatementSeparatesPayrollFromOtherDeposits() {
        List<BankStatementAnalyzer.Deposit> deposits = new BankStatementAnalyzer()
                .deposits(SampleDocs.load("james-carter-bank-statement.txt").fullText(), "Northgate Logistics");

        assertEquals(4, deposits.size());
        assertEquals(2, deposits.stream().filter(BankStatementAnalyzer.Deposit::payroll).count());
        assertTrue(deposits.stream().anyMatch(d -> !d.payroll() && d.amount().compareTo(new BigDecimal("9000.00")) == 0));
    }
}
