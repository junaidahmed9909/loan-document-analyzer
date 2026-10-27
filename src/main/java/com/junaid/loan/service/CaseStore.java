package com.junaid.loan.service;

import com.junaid.loan.core.LoanCaseAnalyzer.CaseAnalysis;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Keeps recent cases in memory, dropping the oldest past a fixed limit.
 * Loan documents are sensitive, so nothing is written to disk by default. A database-backed store
 * could implement the same three methods.
 */
@Component
public class CaseStore {

    public record StoredCase(String id, Instant createdAt, CaseAnalysis analysis, boolean llmUsed, List<String> warnings) {}

    static final int MAX_CASES = 100;

    private final Map<String, StoredCase> cases = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, StoredCase> eldest) {
            return size() > MAX_CASES;
        }
    };

    public synchronized void save(StoredCase c) {
        cases.put(c.id(), c);
    }

    public synchronized Optional<StoredCase> find(String id) {
        return Optional.ofNullable(cases.get(id));
    }

    public synchronized int size() {
        return cases.size();
    }
}
