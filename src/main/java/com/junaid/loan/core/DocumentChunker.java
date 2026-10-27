package com.junaid.loan.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits page text into overlapping chunks for retrieval.
 * It prefers to break between paragraphs, then sentences, then words, so a chunk rarely cuts a fact in half.
 * The overlap carries the tail of one chunk into the next, so facts near a boundary are findable from both.
 */
public final class DocumentChunker {

    private final int maxChars;
    private final int overlapChars;

    public DocumentChunker(int maxChars, int overlapChars) {
        if (maxChars < 100) {
            throw new IllegalArgumentException("maxChars must be at least 100");
        }
        if (overlapChars < 0 || overlapChars >= maxChars / 2) {
            throw new IllegalArgumentException("overlapChars must be between 0 and half of maxChars");
        }
        this.maxChars = maxChars;
        this.overlapChars = overlapChars;
    }

    public static DocumentChunker defaults() {
        return new DocumentChunker(800, 150);
    }

    /**
     * @param pages page texts in order; index 0 is page 1
     */
    public List<Chunk> chunk(String documentName, List<String> pages) {
        List<Chunk> chunks = new ArrayList<>();
        for (int p = 0; p < pages.size(); p++) {
            String text = normalise(pages.get(p));
            if (text.isEmpty()) {
                continue;
            }
            for (String piece : split(text)) {
                chunks.add(new Chunk(documentName + "#" + (chunks.size() + 1), documentName, p + 1, piece));
            }
        }
        return chunks;
    }

    private List<String> split(String text) {
        List<String> out = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());
            if (end < text.length()) {
                end = bestBreak(text, start, end);
            }
            String piece = text.substring(start, end).trim();
            if (!piece.isEmpty()) {
                out.add(piece);
            }
            if (end >= text.length()) {
                break;
            }
            int next = end - overlapChars;
            // Start the overlap on a word boundary, and always move forward.
            while (next > start && next < end && !Character.isWhitespace(text.charAt(next - 1))) {
                next++;
            }
            start = next <= start ? end : next;
        }
        return out;
    }

    /** Finds the latest natural break in the back half of the window. */
    private int bestBreak(String text, int start, int end) {
        int floor = start + maxChars / 2;
        int para = text.lastIndexOf("\n\n", end);
        if (para >= floor) {
            return para + 2;
        }
        for (int i = end - 1; i >= floor; i--) {
            char c = text.charAt(i);
            if ((c == '.' || c == '!' || c == '?' || c == '\n') && i + 1 < text.length() && Character.isWhitespace(text.charAt(i + 1))) {
                return i + 1;
            }
        }
        int space = text.lastIndexOf(' ', end);
        return space >= floor ? space + 1 : end;
    }

    private static String normalise(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\r\n", "\n").replace('\r', '\n').replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }
}
