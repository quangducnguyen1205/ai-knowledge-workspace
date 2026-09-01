package com.aiknowledgeworkspace.workspacecore.integration.fastapi.adapter.out.provider.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class FastApiHealthIndicatorTest {

    private final AtomicInteger responseCode = new AtomicInteger(200);
    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            byte[] body = "{\"status\":\"ok\",\"service\":\"demo-fastapi\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseCode.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private FastApiHealthIndicator indicatorAgainst(String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(1000);
        requestFactory.setReadTimeout(1000);
        return new FastApiHealthIndicator(RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build());
    }

    private String serverBaseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void respondingProcessorHealthEndpointReportsUp() {
        Health health = indicatorAgainst(serverBaseUrl()).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void failingProcessorHealthEndpointReportsDown() {
        responseCode.set(503);

        Health health = indicatorAgainst(serverBaseUrl()).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "UNREACHABLE");
    }

    @Test
    void unreachableProcessorReportsDownWithoutLeakingTheEndpoint() {
        Health health = indicatorAgainst("http://127.0.0.1:1").health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsOnlyKeys("reason");
        assertThat(health.getDetails().toString()).doesNotContain("127.0.0.1");
    }
}
