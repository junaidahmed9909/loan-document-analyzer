package com.junaid.loan.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Loads the fictional sample documents shipped in sample-docs/. Tests run from the project root. */
final class SampleDocs {

    private SampleDocs() {}

    static LoanCaseAnalyzer.SourceDocument load(String fileName) {
        try {
            String text = Files.readString(Path.of("sample-docs", fileName));
            return new LoanCaseAnalyzer.SourceDocument(fileName, List.of(text));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<LoanCaseAnalyzer.SourceDocument> applicant(String prefix) {
        try (var files = Files.list(Path.of("sample-docs"))) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith(prefix))
                    .sorted()
                    .map(SampleDocs::load)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
