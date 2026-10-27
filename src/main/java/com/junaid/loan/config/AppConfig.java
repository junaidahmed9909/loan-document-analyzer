package com.junaid.loan.config;

import com.junaid.loan.core.DocumentChunker;
import com.junaid.loan.core.LoanCaseAnalyzer;
import com.junaid.loan.llm.AnthropicLlmClient;
import com.junaid.loan.llm.LlmClient;
import com.junaid.loan.llm.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    @Bean
    LoanCaseAnalyzer loanCaseAnalyzer() {
        return new LoanCaseAnalyzer(DocumentChunker.defaults());
    }

    @Bean
    LlmClient llmClient(LlmProperties props) {
        if (!props.enabled()) {
            log.info("No LLM API key set: running in search-only mode (rules for extraction, passages for answers)");
            return LlmClient.DISABLED;
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.timeout());
        factory.setReadTimeout(props.timeout());
        log.info("LLM enabled with model {}", props.model());
        return new AnthropicLlmClient(RestClient.builder().requestFactory(factory), props);
    }
}
