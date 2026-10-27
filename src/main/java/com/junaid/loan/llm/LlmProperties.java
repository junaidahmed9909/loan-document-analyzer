package com.junaid.loan.llm;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM settings under the {@code llm} prefix. Leave {@code apiKey} empty to run without AI:
 * extraction falls back to rules only and questions return the best-matching passages.
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        String apiKey,
        String model,
        String baseUrl,
        int maxTokens,
        Duration timeout,
        boolean extractionEnabled) {

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
