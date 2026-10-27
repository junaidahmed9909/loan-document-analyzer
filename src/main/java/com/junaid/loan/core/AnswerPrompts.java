package com.junaid.loan.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds grounded prompts and checks the citations that come back.
 * The model only sees numbered excerpts and must cite them as [1], [2]. Any citation number that doesn't
 * point at a real excerpt is thrown away, and an answer with no valid citations is marked as ungrounded.
 */
public final class AnswerPrompts {

    public static final String ANSWER_SYSTEM = """
            You are a careful loan processing assistant. Answer the question using ONLY the numbered excerpts provided.
            Cite every fact with the excerpt number in square brackets, like [1] or [2][3].
            If the excerpts don't contain the answer, say so plainly instead of guessing.
            Keep answers short and specific. Use exact figures from the excerpts.""";

    public static final String EXTRACTION_SYSTEM = """
            You extract loan application data. Reply with a single JSON object and nothing else.
            Use null for anything not stated in the text. Never estimate or infer values.
            Numbers must be plain JSON numbers without $ or commas. interestRate is a percent (6.5 means 6.5%).
            loanTermMonths is a whole number of months.""";

    private static final Pattern CITATION = Pattern.compile("\\[(\\d+(?:\\s*,\\s*\\d+)*)\\]");

    private AnswerPrompts() {}

    public static String answerUserPrompt(String question, List<Chunk> excerpts) {
        StringBuilder sb = new StringBuilder("Excerpts:\n\n");
        for (int i = 0; i < excerpts.size(); i++) {
            Chunk c = excerpts.get(i);
            sb.append('[').append(i + 1).append("] (").append(c.documentName()).append(", page ").append(c.page()).append(")\n")
                    .append(c.text()).append("\n\n");
        }
        sb.append("Question: ").append(question.trim());
        return sb.toString();
    }

    /** Asks only for the fields still missing, which keeps the call small and avoids overwriting rule-based values. */
    public static String extractionUserPrompt(String documentText, List<String> missingFields, int maxChars) {
        String text = documentText.length() > maxChars ? documentText.substring(0, maxChars) : documentText;
        return "Return a JSON object with exactly these keys: " + String.join(", ", missingFields)
                + "\n\nDocument:\n\"\"\"\n" + text + "\n\"\"\"";
    }

    /** Distinct citation numbers in the answer that point to a real excerpt (1..excerptCount), in ascending order. */
    public static List<Integer> validCitations(String answer, int excerptCount) {
        TreeSet<Integer> found = new TreeSet<>();
        Matcher m = CITATION.matcher(answer);
        while (m.find()) {
            for (String n : m.group(1).split(",")) {
                int idx = Integer.parseInt(n.trim());
                if (idx >= 1 && idx <= excerptCount) {
                    found.add(idx);
                }
            }
        }
        return new ArrayList<>(found);
    }

    /** Strips a ```json fence if the model added one. */
    public static String stripCodeFence(String raw) {
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            int lastFence = s.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                s = s.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return s;
    }

    /** Field accuracy for evaluation: share of expected (non-null) fields that were extracted exactly. */
    public static double fieldAccuracy(LoanFields expected, LoanFields actual) {
        Map<String, Object> exp = expected.asMap();
        Map<String, Object> act = actual.asMap();
        int total = 0;
        int correct = 0;
        for (Map.Entry<String, Object> e : exp.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            total++;
            Object a = act.get(e.getKey());
            if (a != null && sameValue(e.getValue(), a)) {
                correct++;
            }
        }
        return total == 0 ? 1.0 : (double) correct / total;
    }

    private static boolean sameValue(Object expected, Object actual) {
        if (expected instanceof java.math.BigDecimal be && actual instanceof java.math.BigDecimal ba) {
            return be.compareTo(ba) == 0;
        }
        if (expected instanceof String se && actual instanceof String sa) {
            return se.equalsIgnoreCase(sa.trim());
        }
        return expected.equals(actual);
    }
}
