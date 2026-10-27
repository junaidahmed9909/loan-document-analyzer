package com.junaid.loan.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.junaid.loan.api.Views.AnswerView;
import com.junaid.loan.api.Views.Citation;
import com.junaid.loan.core.AnswerPrompts;
import com.junaid.loan.core.Bm25Index;
import com.junaid.loan.core.Chunk;
import com.junaid.loan.core.FieldExtractor;
import com.junaid.loan.core.LoanCaseAnalyzer;
import com.junaid.loan.core.LoanCaseAnalyzer.SourceDocument;
import com.junaid.loan.core.LoanFields;
import com.junaid.loan.llm.LlmClient;
import com.junaid.loan.llm.LlmException;
import com.junaid.loan.llm.LlmProperties;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Ties the pipeline together: text extraction, rule-based analysis, optional LLM gap-filling and grounded Q&A.
 * Every LLM step degrades gracefully, so a missing key or an API outage never breaks the core analysis.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    static final int TOP_K = 5;
    static final int EXTRACTION_MAX_CHARS = 12_000;

    /** A named, already-read upload. Keeps the service independent of the web layer. */
    public record Upload(String fileName, byte[] content) {}

    private final DocumentTextExtractor textExtractor;
    private final LoanCaseAnalyzer analyzer;
    private final FieldExtractor fieldExtractor = new FieldExtractor();
    private final LlmClient llm;
    private final LlmProperties llmProps;
    private final CaseStore store;
    private final ObjectMapper json;
    private final Clock clock;

    public AnalysisService(DocumentTextExtractor textExtractor, LoanCaseAnalyzer analyzer, LlmClient llm,
                           LlmProperties llmProps, CaseStore store, ObjectMapper json) {
        this.textExtractor = textExtractor;
        this.analyzer = analyzer;
        this.llm = llm;
        this.llmProps = llmProps;
        this.store = store;
        this.json = json.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.clock = Clock.systemUTC();
    }

    public CaseStore.StoredCase analyze(List<Upload> uploads) {
        if (uploads == null || uploads.isEmpty()) {
            throw new IllegalArgumentException("Upload at least one document");
        }
        List<String> warnings = new ArrayList<>();
        List<SourceDocument> docs = new ArrayList<>();
        for (Upload u : uploads) {
            List<String> pages = textExtractor.extractPages(u.fileName(), u.content());
            if (pages.stream().allMatch(String::isBlank)) {
                warnings.add(u.fileName() + ": no text found. Scanned images need OCR, which isn't supported yet.");
            }
            docs.add(new SourceDocument(u.fileName(), pages));
        }

        boolean useLlm = llm.isAvailable() && llmProps.extractionEnabled();
        List<LoanFields> extras = useLlm ? llmFillGaps(docs, warnings) : List.of();
        LoanCaseAnalyzer.CaseAnalysis analysis = analyzer.analyze(docs, extras);

        CaseStore.StoredCase stored = new CaseStore.StoredCase(UUID.randomUUID().toString(), clock.instant(), analysis, useLlm, warnings);
        store.save(stored);
        return stored;
    }

    public CaseStore.StoredCase get(String id) {
        return store.find(id).orElseThrow(() -> new CaseNotFoundException(id));
    }

    public AnswerView ask(String caseId, String question) {
        Bm25Index index = get(caseId).analysis().index();
        List<Bm25Index.Hit> hits = index.search(question, TOP_K);
        if (hits.isEmpty()) {
            return new AnswerView(question, "I couldn't find anything in these documents about that.", false,
                    llm.isAvailable() ? "llm" : "search-only", List.of());
        }
        List<Chunk> excerpts = hits.stream().map(Bm25Index.Hit::chunk).toList();

        if (!llm.isAvailable()) {
            return new AnswerView(question,
                    "AI answers are turned off (no API key), so here are the most relevant passages from the documents.",
                    true, "search-only", citations(excerpts, null));
        }

        String answer = llm.complete(AnswerPrompts.ANSWER_SYSTEM, AnswerPrompts.answerUserPrompt(question, excerpts));
        List<Integer> cited = AnswerPrompts.validCitations(answer, excerpts.size());
        return new AnswerView(question, answer, !cited.isEmpty(), "llm", citations(excerpts, cited));
    }

    /** Asks the model only for fields the rules missed, one document at a time. Failures become warnings. */
    private List<LoanFields> llmFillGaps(List<SourceDocument> docs, List<String> warnings) {
        List<LoanFields> extras = new ArrayList<>();
        for (SourceDocument doc : docs) {
            LoanFields ruleFields = fieldExtractor.extract(doc.fullText()).fields();
            List<String> missing = ruleFields.missingFields();
            if (missing.isEmpty() || doc.fullText().isBlank()) {
                extras.add(LoanFields.empty());
                continue;
            }
            try {
                String reply = llm.complete(AnswerPrompts.EXTRACTION_SYSTEM,
                        AnswerPrompts.extractionUserPrompt(doc.fullText(), missing, EXTRACTION_MAX_CHARS));
                extras.add(json.readValue(AnswerPrompts.stripCodeFence(reply), LoanFields.class));
            } catch (LlmException | com.fasterxml.jackson.core.JsonProcessingException e) {
                log.warn("LLM extraction skipped for {}: {}", doc.name(), e.getMessage());
                warnings.add(doc.name() + ": AI extraction skipped (" + e.getMessage() + "); rule-based values were used.");
                extras.add(LoanFields.empty());
            }
        }
        return extras;
    }

    private static List<Citation> citations(List<Chunk> excerpts, List<Integer> onlyThese) {
        List<Citation> out = new ArrayList<>();
        for (int i = 0; i < excerpts.size(); i++) {
            if (onlyThese == null || onlyThese.contains(i + 1)) {
                Chunk c = excerpts.get(i);
                out.add(new Citation(i + 1, c.documentName(), c.page(), c.text()));
            }
        }
        return out;
    }
}
