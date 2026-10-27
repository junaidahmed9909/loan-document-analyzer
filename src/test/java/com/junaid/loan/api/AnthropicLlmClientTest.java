package com.junaid.loan.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.junaid.loan.llm.AnthropicLlmClient;
import com.junaid.loan.llm.LlmException;
import com.junaid.loan.llm.LlmProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Checks the exact request we send to the Messages API and how replies are read, without network calls. */
class AnthropicLlmClientTest {

    private MockRestServiceServer server;
    private AnthropicLlmClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        LlmProperties props = new LlmProperties("test-key", "claude-haiku-5-5", "https://api.example.test", 512, Duration.ofSeconds(5), true);
        client = new AnthropicLlmClient(builder, props);
    }

    @Test
    void sendsMessagesRequestAndJoinsTextBlocks() {
        server.expect(requestTo("https://api.example.test/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "test-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(jsonPath("$.model").value("claude-haiku-5-5"))
                .andExpect(jsonPath("$.max_tokens").value(512))
                .andExpect(jsonPath("$.system").value("sys"))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("hello"))
                .andRespond(withSuccess("""
                        {"content":[{"type":"text","text":"The rate is 6.25% "},{"type":"text","text":"[1]"}],
                         "stop_reason":"end_turn"}""", MediaType.APPLICATION_JSON));

        assertEquals("The rate is 6.25% [1]", client.complete("sys", "hello"));
        server.verify();
    }

    @Test
    void wrapsHttpErrors() {
        server.expect(requestTo("https://api.example.test/v1/messages")).andRespond(withServerError());
        assertThrows(LlmException.class, () -> client.complete("sys", "hello"));
    }

    @Test
    void refusalBecomesAnError() {
        server.expect(requestTo("https://api.example.test/v1/messages"))
                .andRespond(withSuccess("{\"content\":[],\"stop_reason\":\"refusal\"}", MediaType.APPLICATION_JSON));
        assertThrows(LlmException.class, () -> client.complete("sys", "hello"));
    }
}
