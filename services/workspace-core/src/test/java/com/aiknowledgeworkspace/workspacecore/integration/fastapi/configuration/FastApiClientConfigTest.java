package com.aiknowledgeworkspace.workspacecore.integration.fastapi.adapter.out.provider.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class FastApiClientConfigTest {

    private final FastApiClientConfig config = new FastApiClientConfig();
    private final Map<String, List<String>> authorizationHeadersByPath = new ConcurrentHashMap<>();
    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            authorizationHeadersByPath.put(
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getOrDefault("Authorization", List.of())
            );
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private FastApiProperties propertiesWithToken(String internalToken) {
        FastApiProperties properties = new FastApiProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setInternalToken(internalToken);
        return properties;
    }

    @Test
    void assistantRestClientUsesAssistantSpecificReadTimeout() {
        FastApiProperties properties = spy(new FastApiProperties());
        properties.setConnectTimeout(Duration.ofSeconds(5));
        properties.setReadTimeout(Duration.ofSeconds(30));
        properties.setAssistantReadTimeout(Duration.ofSeconds(75));

        RestClient client = new FastApiClientConfig().fastApiAssistantRestClient(properties);

        assertThat(client).isNotNull();
        verify(properties).getConnectTimeout();
        verify(properties).getAssistantReadTimeout();
        verify(properties, never()).getReadTimeout();
    }

    @Test
    void attachesBearerCredentialToBothClientsWhenTokenIsConfigured() {
        FastApiProperties properties = propertiesWithToken("local-test-internal-token");

        config.fastApiRestClient(properties).get().uri("/processing").retrieve().toBodilessEntity();
        config.fastApiAssistantRestClient(properties).get().uri("/assistant").retrieve().toBodilessEntity();

        assertThat(authorizationHeadersByPath.get("/processing"))
                .containsExactly("Bearer local-test-internal-token");
        assertThat(authorizationHeadersByPath.get("/assistant"))
                .containsExactly("Bearer local-test-internal-token");
    }

    @Test
    void sendsNoAuthorizationHeaderWhenTokenIsBlank() {
        config.fastApiRestClient(propertiesWithToken(" ")).get().uri("/processing").retrieve().toBodilessEntity();

        assertThat(authorizationHeadersByPath.get("/processing")).isEmpty();
    }

    @Test
    void healthProbeClientNeverSendsTheInternalBearerTokenEvenWhenConfigured() {
        FastApiProperties properties = propertiesWithToken("local-test-internal-token");

        config.fastApiHealthRestClient(properties).get().uri("/health").retrieve().toBodilessEntity();

        assertThat(authorizationHeadersByPath.get("/health")).isEmpty();
    }

    @Test
    void healthProbeClientUsesItsOwnShortReadTimeoutNotTheProcessingOne() {
        FastApiProperties properties = spy(new FastApiProperties());
        properties.setConnectTimeout(Duration.ofSeconds(5));
        properties.setReadTimeout(Duration.ofSeconds(30));

        RestClient client = new FastApiClientConfig().fastApiHealthRestClient(properties);

        assertThat(client).isNotNull();
        verify(properties).getConnectTimeout();
        verify(properties, never()).getReadTimeout();
        verify(properties, never()).getAssistantReadTimeout();
    }
}
