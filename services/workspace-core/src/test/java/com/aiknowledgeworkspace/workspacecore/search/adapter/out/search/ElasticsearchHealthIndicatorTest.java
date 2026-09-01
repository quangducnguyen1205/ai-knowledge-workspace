package com.aiknowledgeworkspace.workspacecore.search.adapter.out.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class ElasticsearchHealthIndicatorTest {

    private final AtomicReference<String> clusterHealthBody =
            new AtomicReference<>("{\"status\":\"green\"}");
    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/_cluster/health", exchange -> {
            byte[] body = clusterHealthBody.get().getBytes(StandardCharsets.UTF_8);
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

    private ElasticsearchHealthIndicator indicatorAgainst(String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(1000);
        requestFactory.setReadTimeout(1000);
        return new ElasticsearchHealthIndicator(RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build());
    }

    private String serverBaseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void greenClusterReportsUp() {
        clusterHealthBody.set("{\"status\":\"green\"}");

        Health health = indicatorAgainst(serverBaseUrl()).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("clusterStatus", "green");
    }

    @Test
    void yellowClusterReportsUpBecauseSingleNodeYellowIsTheLocalNorm() {
        clusterHealthBody.set("{\"status\":\"yellow\"}");

        Health health = indicatorAgainst(serverBaseUrl()).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("clusterStatus", "yellow");
    }

    @Test
    void redClusterReportsDown() {
        clusterHealthBody.set("{\"status\":\"red\"}");

        Health health = indicatorAgainst(serverBaseUrl()).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("clusterStatus", "red");
    }

    @Test
    void responseWithoutAClusterStatusReportsDownAsUnknown() {
        clusterHealthBody.set("{}");

        Health health = indicatorAgainst(serverBaseUrl()).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("clusterStatus", "unknown");
    }

    @Test
    void unreachableClusterReportsDownWithoutLeakingTheEndpoint() {
        Health health = indicatorAgainst("http://127.0.0.1:1").health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsOnlyKeys("reason");
        assertThat(health.getDetails().toString()).doesNotContain("127.0.0.1");
    }
}
