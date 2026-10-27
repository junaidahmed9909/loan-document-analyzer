package com.junaid.loan.core;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Measures rule-based extraction against a labelled set of documents.
 * The set in sample-docs/eval uses deliberately varied wording ("Works at", "Yearly Gross Pay"),
 * so the score shows how the rules hold up on layouts they weren't written for.
 *
 * Usage: java -cp target/classes com.junaid.loan.core.ExtractionEval [evalDir]
 */
public final class ExtractionEval {

    public record Result(int expected, int correct, Map<String, int[]> byField, List<String> misses) {
        public double accuracy() {
            return expected == 0 ? 1.0 : (double) correct / expected;
        }
    }

    private ExtractionEval() {}

    public static Result run(Path dir) throws IOException {
        FieldExtractor extractor = new FieldExtractor();
        Map<String, int[]> byField = new TreeMap<>();
        List<String> misses = new ArrayList<>();
        int expected = 0;
        int correct = 0;
        List<String> lines = Files.readAllLines(dir.resolve("expected.csv"));
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split(",", 3);
            String file = parts[0];
            String field = parts[1];
            String want = parts[2].trim();
            Object got = extractor.extract(Files.readString(dir.resolve(file))).fields().asMap().get(field);
            boolean ok = matches(want, got);
            expected++;
            int[] counts = byField.computeIfAbsent(field, k -> new int[2]);
            counts[1]++;
            if (ok) {
                correct++;
                counts[0]++;
            } else {
                misses.add(file + " " + field + ": expected " + want + ", got " + got);
            }
        }
        return new Result(expected, correct, byField, misses);
    }

    private static boolean matches(String want, Object got) {
        if (got == null) {
            return false;
        }
        if (got instanceof BigDecimal bd) {
            return bd.compareTo(new BigDecimal(want)) == 0;
        }
        if (got instanceof Integer i) {
            return i == Integer.parseInt(want);
        }
        return want.equalsIgnoreCase(got.toString().trim());
    }

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "sample-docs/eval");
        Result r = run(dir);
        long docs;
        try (var files = Files.list(dir)) {
            docs = files.filter(p -> p.toString().endsWith(".txt")).count();
        }
        System.out.printf(Locale.ROOT, "Documents: %d, labelled fields: %d%n", docs, r.expected());
        System.out.printf(Locale.ROOT, "Rule-based field accuracy: %d/%d = %.1f%%%n", r.correct(), r.expected(), r.accuracy() * 100);
        System.out.println("By field (correct/expected):");
        r.byField().forEach((f, c) -> System.out.printf(Locale.ROOT, "  %-20s %d/%d%n", f, c[0], c[1]));
        System.out.println("Misses:");
        r.misses().forEach(m -> System.out.println("  " + m));
    }
}
