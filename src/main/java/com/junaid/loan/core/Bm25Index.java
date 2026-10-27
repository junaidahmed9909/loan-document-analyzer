package com.junaid.loan.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Keyword search over chunks using BM25, the ranking function behind most search engines.
 *
 * Why not embeddings? Loan questions are full of exact terms (employer names, "gross pay", dollar figures)
 * where keyword ranking does very well. It also needs no extra API key, runs in microseconds and is fully
 * deterministic, which makes answers reproducible. The {@code Retriever} seam in the service makes it easy
 * to add vector search later.
 */
public final class Bm25Index {

    public record Hit(Chunk chunk, double score) {}

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "did", "do", "does", "for", "from", "has", "have", "how",
            "i", "in", "is", "it", "its", "of", "on", "or", "the", "this", "that", "to", "was", "what", "when",
            "where", "which", "who", "why", "with", "you", "your", "me", "my", "there", "their", "much", "many");

    private final List<Chunk> chunks;
    private final List<Map<String, Integer>> termFreqs = new ArrayList<>();
    private final Map<String, Integer> docFreq = new HashMap<>();
    private final int[] lengths;
    private final double avgLength;
    private final double k1;
    private final double b;

    public Bm25Index(List<Chunk> chunks) {
        this(chunks, 1.2, 0.75);
    }

    public Bm25Index(List<Chunk> chunks, double k1, double b) {
        this.chunks = List.copyOf(chunks);
        this.k1 = k1;
        this.b = b;
        this.lengths = new int[this.chunks.size()];
        long total = 0;
        for (int i = 0; i < this.chunks.size(); i++) {
            List<String> tokens = tokenize(this.chunks.get(i).text());
            Map<String, Integer> tf = new HashMap<>();
            for (String t : tokens) {
                tf.merge(t, 1, Integer::sum);
            }
            for (String t : tf.keySet()) {
                docFreq.merge(t, 1, Integer::sum);
            }
            termFreqs.add(tf);
            lengths[i] = tokens.size();
            total += tokens.size();
        }
        this.avgLength = this.chunks.isEmpty() ? 0 : (double) total / this.chunks.size();
    }

    public int size() {
        return chunks.size();
    }

    /** Returns up to {@code topK} chunks with a positive score, best first. Ties keep document order. */
    public List<Hit> search(String query, int topK) {
        List<String> terms = tokenize(query);
        List<Hit> hits = new ArrayList<>();
        if (terms.isEmpty() || chunks.isEmpty()) {
            return hits;
        }
        int n = chunks.size();
        for (int i = 0; i < n; i++) {
            Map<String, Integer> tf = termFreqs.get(i);
            double score = 0;
            for (String term : terms) {
                Integer f = tf.get(term);
                if (f == null) {
                    continue;
                }
                int df = docFreq.get(term);
                double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
                double norm = f * (k1 + 1) / (f + k1 * (1 - b + b * lengths[i] / avgLength));
                score += idf * norm;
            }
            if (score > 0) {
                hits.add(new Hit(chunks.get(i), score));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        return hits.size() > topK ? new ArrayList<>(hits.subList(0, topK)) : hits;
    }

    /** Lowercases, splits on anything that isn't a letter or digit, drops stopwords and folds simple plurals. */
    static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (raw.isEmpty() || STOPWORDS.contains(raw)) {
                continue;
            }
            out.add(stem(raw));
        }
        return out;
    }

    private static String stem(String w) {
        if (w.length() > 4 && w.endsWith("ies")) {
            return w.substring(0, w.length() - 3) + "y";
        }
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us")) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }
}
