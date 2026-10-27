package com.junaid.loan.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoanCaseAnalyzerTest {

    private final LoanCaseAnalyzer analyzer = new LoanCaseAnalyzer(DocumentChunker.defaults());

    @Test
    void cleanApplicationIsLowRisk() {
        LoanCaseAnalyzer.CaseAnalysis a = analyzer.analyze(SampleDocs.applicant("maria-lopez"));

        assertEquals(RiskAssessment.Level.LOW, a.risk().level(), a.risk().flags().toString());
        assertEquals(new BigDecimal("80.00"), a.risk().ltvPercent());
        assertEquals(new BigDecimal("1970.30"), a.risk().estimatedMonthlyPayment());
        assertEquals("Maria Lopez", a.merged().applicantName());
        assertEquals(3, a.documents().size());
        assertTrue(a.index().size() > 0);
    }

    @Test
    void riskyApplicationRaisesTheRightFlags() {
        LoanCaseAnalyzer.CaseAnalysis a = analyzer.analyze(SampleDocs.applicant("james-carter"));
        RiskAssessment r = a.risk();

        assertEquals(RiskAssessment.Level.HIGH, r.level());
        assertTrue(r.hasFlag("INCOME_MISMATCH"), r.flags().toString());
        assertTrue(r.hasFlag("HIGH_DTI"), r.flags().toString());
        assertTrue(r.hasFlag("LTV_ABOVE_80"), r.flags().toString());
        assertTrue(r.hasFlag("FAIR_CREDIT_SCORE"), r.flags().toString());
        assertEquals(2, r.flags().stream().filter(f -> f.code().equals("LARGE_DEPOSIT")).count());
        assertFalse(r.hasFlag("HIGH_LTV"));
    }

    @Test
    void missingRateMeansDtiUsesExistingDebtsOnly() {
        RiskAssessment r = analyzer.analyze(SampleDocs.applicant("priya-shah")).risk();

        assertEquals(new BigDecimal("50.00"), r.dtiPercent());
        assertNull(r.estimatedMonthlyPayment());
        assertTrue(r.hasFlag("HIGH_DTI"));
        assertTrue(r.hasFlag("LOW_CREDIT_SCORE"));
        assertTrue(r.hasFlag("MISSING_DATA"));
    }

    @Test
    void noIncomeAtAllIsHighRisk() {
        LoanCaseAnalyzer.CaseAnalysis a = analyzer.analyze(List.of(
                new LoanCaseAnalyzer.SourceDocument("note.txt", List.of("Loan Amount: $10,000"))));
        assertTrue(a.risk().hasFlag("MISSING_INCOME"));
        assertEquals(RiskAssessment.Level.HIGH, a.risk().level());
    }

    @Test
    void extraFieldsOnlyFillGaps() {
        LoanFields llm = new LoanFields("Wrong Name", null, null, null, null, null, new BigDecimal("500000"), null, null, null);
        LoanCaseAnalyzer.CaseAnalysis a = analyzer.analyze(
                List.of(SampleDocs.load("priya-shah-application.txt")), List.of(llm));

        assertEquals("Priya Shah", a.merged().applicantName(), "rule-based values win");
        assertEquals(new BigDecimal("500000"), a.merged().propertyValue(), "gaps are filled");
    }

    @Test
    void rejectsEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> analyzer.analyze(List.of()));
    }

    @Test
    void searchFindsTheRightPassage() {
        LoanCaseAnalyzer.CaseAnalysis a = analyzer.analyze(SampleDocs.applicant("james-carter"));
        List<Bm25Index.Hit> hits = a.index().search("Where is the down payment coming from? gift", 3);

        assertFalse(hits.isEmpty());
        assertTrue(hits.get(0).chunk().text().contains("family gift"), hits.get(0).chunk().text());
    }
}
