package com.junaid.loan.core;

import java.util.Objects;

/**
 * A searchable slice of a document.
 *
 * @param documentName file the text came from, shown in citations
 * @param page         1-based page number
 */
public record Chunk(String id, String documentName, int page, String text) {

    public Chunk {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(documentName, "documentName is required");
        Objects.requireNonNull(text, "text is required");
        if (page < 1) {
            throw new IllegalArgumentException("page must be 1 or more");
        }
    }
}
