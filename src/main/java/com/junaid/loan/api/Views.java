package com.junaid.loan.api;

import com.junaid.loan.core.BankStatementAnalyzer;
import com.junaid.loan.core.LoanCaseAnalyzer;
import com.junaid.loan.core.LoanFields;
import com.junaid.loan.core.RiskAssessment;
import com.junaid.loan.service.CaseStore;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Response shapes for the API. Kept separate from the core types so the JSON stays stable. */
public final class Views {

    private Views() {}

    public record DocumentView(String name, int pages, Map<String, Object> fields, Map<String, String> evidence,
                               List<BankStatementAnalyzer.Deposit> deposits) {
        static DocumentView of(LoanCaseAnalyzer.DocumentResult d) {
            return new DocumentView(d.name(), d.pages(), d.extraction().fields().asMap(), d.extraction().evidence(), d.deposits());
        }
    }

    public record CaseView(String id, Instant createdAt, Map<String, Object> applicant, RiskAssessment risk,
                           List<DocumentView> documents, int searchableChunks, boolean llmUsed, List<String> warnings) {
        public static CaseView of(CaseStore.StoredCase c) {
            LoanFields merged = c.analysis().merged();
            return new CaseView(c.id(), c.createdAt(), merged.asMap(), c.analysis().risk(),
                    c.analysis().documents().stream().map(DocumentView::of).toList(),
                    c.analysis().chunks().size(), c.llmUsed(), c.warnings());
        }
    }

    public record Citation(int number, String documentName, int page, String excerpt) {}

    /**
     * @param grounded true when every claim could be tied to at least one real excerpt
     * @param mode     "llm" when the model wrote the answer, "search-only" when no API key is configured
     */
    public record AnswerView(String question, String answer, boolean grounded, String mode, List<Citation> citations) {}
}
