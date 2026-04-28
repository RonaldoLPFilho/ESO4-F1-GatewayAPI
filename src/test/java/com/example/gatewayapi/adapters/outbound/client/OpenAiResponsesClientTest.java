package com.example.gatewayapi.adapters.outbound.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAiResponsesClientTest {

    @Test
    void shouldExtractOutputTextFromResponsesPayload() {
        ExchangeFunction exchangeFunction = request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("""
                                {
                                  "output": [
                                    {
                                      "content": [
                                        {
                                          "type": "output_text",
                                          "text": "Relatorio gerado pela OpenAI"
                                        }
                                      ]
                                    }
                                  ]
                                }
                                """)
                        .build()
        );

        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();

        OpenAiResponsesClient client = new OpenAiResponsesClient(
                webClient,
                new ObjectMapper(),
                "test-key",
                "gpt-test"
        );

        String result = client.generateMarkdownReport("instrucao", "contexto")
                .block();

        assertEquals("Relatorio gerado pela OpenAI", result);
    }
}
