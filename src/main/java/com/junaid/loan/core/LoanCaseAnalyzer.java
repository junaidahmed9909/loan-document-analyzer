package com.junaid.loan.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runs the whole rule-based pipeline over a set of documents for one applicant:
 * chunk and index the text, extract fields from each document, merge them, find deposits and assess risk.
 * The LLM steps in the service build on top of this; everything here works with no API key.
 */
public final class LoanCaseAnalyzer {

    /** One uploaded document, already converted to text. Index 0 of pages is page 1. */
    public record SourceDocument(String name, List<String> pages) {
        public SourceDocument {
            Objects.requireNonNull(name, "name is required");
            pages = List.copyOf(pages);
        }

        public String fullText() {
            return String.join("\n\n", pages);
        }
    }

    public record DocumentResult(String name, int pages, FieldExtractor.Extraction extraction,
                                 List<BankStatementAnalyzer.Deposit> deposits) {}

    public record CaseAnalysis(List<DocumentResult> documents, LoanFields merged, RiskAssessment risk,
                               List<Chunk> chunks, Bm25Index index) {}

    private final DocumentChunker chunker;
    private final FieldExtractor extractor = new FieldExtractor();
    private final BankStatementAnalyzer bankAnalyzer = new BankStatementAnalyzer();
    private final RiskAnalyzer riskAnalyzer = new RiskAnalyzer();

    public LoanCaseAnalyzer(DocumentChunker chunker) {
        this.chunker = Objects.requireNonNull(chunker);
    }

    public CaseAnalysis analyze(List<SourceDocument> docs) {
        return analyze(docs, List.of());
    }

    /**
     * @param extraFields per-document fields found by other means (for example an LLM), used only to fill gaps.
     *                    Either empty, or one entry per document in the same order.
     */
    public CaseAnalysis analyze(List<SourceDocument> docs, List<LoanFields> extraFields) {
        if (docs == null || docs.isEmpty()) {
            throw new IllegalArgumentException("At least one document is required");
        }
        if (!extraFields.isEmpty() && extraFields.size() != docs.size()) {
            throw new IllegalArgumentException("extraFields must be empty or match the number of documents");
        }
        List<Chunk> chunks = new ArrayList<>();
        List<FieldExtractor.Extraction> extractions = new ArrayList<>();
        List<LoanFields> perDocument = new ArrayList<>();
        LoanFields merged = LoanFields.empty();

        for (int i = 0; i < docs.size(); i++) {
            SourceDocument doc = docs.get(i);
            chunks.addAll(chunker.chunk(doc.name(), doc.pages()));
            FieldExtractor.Extraction ex = extractor.extract(doc.fullText());
            LoanFields fields = extraFields.isEmpty() ? ex.fields() : ex.fields().fillFrom(extraFields.get(i));
            extractions.add(ex);
            perDocument.add(fields);
            merged = merged.fillFrom(fields);
        }

        // Deposits need the employer to tell payroll apart, so they run after merging.
        List<DocumentResult> results = new ArrayList<>();
        List<BankStatementAnalyzer.Deposit> allDeposits = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            SourceDocument doc = docs.get(i);
            List<BankStatementAnalyzer.Deposit> deposits = bankAnalyzer.deposits(doc.fullText(), merged.employer());
            allDeposits.addAll(deposits);
            FieldExtractor.Extraction ex = extractions.get(i);
            results.add(new DocumentResult(doc.name(), doc.pages().size(),
                    new FieldExtractor.Extraction(perDocument.get(i), ex.evidence()), deposits));
        }

        RiskAssessment risk = riskAnalyzer.assess(merged, perDocument, allDeposits);
        return new CaseAnalysis(results, merged, risk, chunks, new Bm25Index(chunks));
    }
}
