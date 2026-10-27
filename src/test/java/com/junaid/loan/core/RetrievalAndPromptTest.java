package com.junaid.loan.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RetrievalAndPromptTest {

    @Test
    void chunkerRespectsSizeAndKeepsPageNumbers() {
        String para = "The borrower has worked at the same employer for six years. ".repeat(40);
        List<Chunk> chunks = new DocumentChunker(300, 50).chunk("doc.txt", List.of("short page one", para));

        assertEquals(1, chunks.get(0).page());
        assertTrue(chunks.size() > 3);
        for (Chunk c : chunks) {
            assertTrue(c.text().length() <= 300, "chunk too long: " + c.text().length());
        }
        assertEquals(2, chunks.get(chunks.size() - 1).page());
    }

    @Test
    void chunkerOverlapsNeighbours() {
        String text = "Alpha bravo charlie delta echo foxtrot golf hotel india juliet kilo lima mike. ".repeat(10);
        List<Chunk> chunks = new DocumentChunker(200, 60).chunk("d", List.of(text));
        String endOfFirst = chunks.get(0).text().substring(chunks.get(0).text().length() - 20);
        assertTrue(chunks.get(1).text().contains(endOfFirst.trim().split(" ")[1]));
    }

    @Test
    void chunkerSkipsBlankPagesAndRejectsBadSettings() {
        assertTrue(DocumentChunker.defaults().chunk("d", List.of("   ", "")).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new DocumentChunker(50, 0));
        assertThrows(IllegalArgumentException.class, () -> new DocumentChunker(200, 150));
    }

    @Test
    void bm25RanksRareTermsHigher() {
        List<Chunk> chunks = List.of(
                new Chunk("1", "a", 1, "income income income salary paid monthly"),
                new Chunk("2", "a", 1, "appraisal of the property came in at the purchase price"),
                new Chunk("3", "a", 1, "income verified by pay stubs"));
        List<Bm25Index.Hit> hits = new Bm25Index(chunks).search("What did the appraisal say?", 2);

        assertEquals(1, hits.size());
        assertEquals("2", hits.get(0).chunk().id());
    }

    @Test
    void tokenizerFoldsPluralsAndDropsStopwords() {
        assertEquals(List.of("stub", "liability", "pay"), Bm25Index.tokenize("The stubs, liabilities and pays"));
    }

    @Test
    void promptNumbersExcerptsWithSource() {
        String prompt = AnswerPrompts.answerUserPrompt("  What is the rate? ",
                List.of(new Chunk("x", "app.pdf", 2, "Interest Rate: 6.25%")));
        assertTrue(prompt.contains("[1] (app.pdf, page 2)"));
        assertTrue(prompt.endsWith("Question: What is the rate?"));
    }

    @Test
    void citationsOutsideTheExcerptsAreDropped() {
        assertEquals(List.of(1, 2), AnswerPrompts.validCitations("Rate is 6.25% [2][1]. Income [1, 7]. Bogus [0]", 3));
        assertTrue(AnswerPrompts.validCitations("No citations here", 3).isEmpty());
    }

    @Test
    void stripsJsonFences() {
        assertEquals("{\"a\":1}", AnswerPrompts.stripCodeFence("```json\n{\"a\":1}\n```"));
        assertEquals("{\"a\":1}", AnswerPrompts.stripCodeFence("{\"a\":1}"));
    }
}
