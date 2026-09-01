package com.aiknowledgeworkspace.workspacecore;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Liveness/readiness semantics, proven over real HTTP against the real security chain and the
 * real actuator surface. The contract under test: liveness answers "is this process alive"
 * and never depends on a remote service; readiness answers "can this instance take core
 * product traffic" and fails exactly when canonical PostgreSQL state is unavailable;
 * capability dependencies (Elasticsearch, FastAPI) degrade their own component without
 * removing the instance from traffic. Both capability endpoints point at a closed port so
 * their DOWN state is deterministic, and the datasource is a toggleable wrapper so a
 * PostgreSQL outage and its recovery can be exercised without a JVM restart and without
 * touching the developer's persistent database.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.flyway.enabled=true",
                "integration.elasticsearch.base-url=http://127.0.0.1:1",
                "integration.fastapi.base-url=http://127.0.0.1:1"
        })
class HealthProbeSemanticsTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @TestConfiguration
    static class ToggleableDataSourceConfig {

        static final AtomicBoolean DATABASE_UNAVAILABLE = new AtomicBoolean(false);

        @Bean(destroyMethod = "close")
        ToggleableDataSource dataSource() {
            HikariDataSource delegate = new HikariDataSource();
            delegate.setJdbcUrl("jdbc:h2:mem:health-probe-semantics;MODE=PostgreSQL;"
                    + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH");
            delegate.setUsername("sa");
            delegate.setPassword("");
            return new ToggleableDataSource(delegate, DATABASE_UNAVAILABLE);
        }
    }

    static class ToggleableDataSource extends DelegatingDataSource implements AutoCloseable {

        private final HikariDataSource delegate;
        private final AtomicBoolean unavailable;

        ToggleableDataSource(HikariDataSource delegate, AtomicBoolean unavailable) {
            super(delegate);
            this.delegate = delegate;
            this.unavailable = unavailable;
        }

        @Override
        public Connection getConnection() throws SQLException {
            failWhenUnavailable();
            return super.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            failWhenUnavailable();
            return super.getConnection(username, password);
        }

        private void failWhenUnavailable() throws SQLException {
            if (unavailable.get()) {
                throw new SQLException("simulated PostgreSQL outage");
            }
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private ResponseEntity<String> get(String path) {
        return restTemplate.getForEntity(path, String.class);
    }

    @Test
    void livenessAnswersProcessAliveIndependentlyOfRemoteDependencies() {
        ResponseEntity<String> liveness = get("/actuator/health/liveness");

        assertThat(liveness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JsonPath.<String>read(liveness.getBody(), "$.status")).isEqualTo("UP");
    }

    @Test
    void readinessContainsExactlyTheCriticalDependenciesAndIsUpWhenTheyAre() {
        ResponseEntity<String> readiness = get("/actuator/health/readiness");

        assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JsonPath.<String>read(readiness.getBody(), "$.status")).isEqualTo("UP");
        assertThat(JsonPath.<String>read(readiness.getBody(), "$.components.readinessState.status"))
                .isEqualTo("UP");
        assertThat(JsonPath.<String>read(readiness.getBody(), "$.components.db.status")).isEqualTo("UP");
        assertThat(readiness.getBody())
                .doesNotContain("elasticsearch")
                .doesNotContain("fastapi");
    }

    @Test
    void unavailableCapabilityDegradesItsComponentWithoutRemovingTheInstanceFromTraffic() {
        ResponseEntity<String> aggregate = get("/actuator/health");

        assertThat(aggregate.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(JsonPath.<String>read(aggregate.getBody(), "$.status")).isEqualTo("DOWN");
        assertThat(JsonPath.<String>read(aggregate.getBody(), "$.components.elasticsearch.status"))
                .isEqualTo("DOWN");
        assertThat(JsonPath.<String>read(aggregate.getBody(), "$.components.fastapi.status"))
                .isEqualTo("DOWN");
        assertThat(JsonPath.<String>read(aggregate.getBody(), "$.components.db.status")).isEqualTo("UP");
        // Component names and statuses only — an unauthenticated surface never carries
        // endpoints, exception text, or indicator detail.
        assertThat(aggregate.getBody())
                .doesNotContain("127.0.0.1")
                .doesNotContain("UNREACHABLE")
                .doesNotContain("Connection refused");

        assertThat(get("/actuator/health/liveness").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/actuator/health/readiness").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void postgresOutageFailsReadinessKeepsLivenessAndRecoversWithoutRestart() {
        try {
            ToggleableDataSourceConfig.DATABASE_UNAVAILABLE.set(true);

            ResponseEntity<String> readiness = get("/actuator/health/readiness");
            assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(JsonPath.<String>read(readiness.getBody(), "$.status")).isEqualTo("DOWN");
            assertThat(JsonPath.<String>read(readiness.getBody(), "$.components.db.status"))
                    .isEqualTo("DOWN");
            assertThat(readiness.getBody())
                    .doesNotContain("simulated")
                    .doesNotContain("jdbc")
                    .doesNotContain("SQLException");

            assertThat(get("/actuator/health/liveness").getStatusCode()).isEqualTo(HttpStatus.OK);

            ResponseEntity<String> alias = get("/health");
            assertThat(alias.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(JsonPath.<String>read(alias.getBody(), "$.status")).isEqualTo("DOWN");
            assertThat(JsonPath.<String>read(alias.getBody(), "$.service")).isEqualTo("workspace-core");
        } finally {
            ToggleableDataSourceConfig.DATABASE_UNAVAILABLE.set(false);
        }

        ResponseEntity<String> recovered = get("/actuator/health/readiness");
        assertThat(recovered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JsonPath.<String>read(recovered.getBody(), "$.status")).isEqualTo("UP");
        assertThat(get("/health").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void legacyHealthAliasReportsReadinessTruthWithTheCompatibilityShape() {
        ResponseEntity<String> alias = get("/health");

        assertThat(alias.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JsonPath.<String>read(alias.getBody(), "$.status")).isEqualTo("UP");
        assertThat(JsonPath.<String>read(alias.getBody(), "$.service")).isEqualTo("workspace-core");
    }

    @Test
    void sensitiveManagementEndpointsAreNotExposedAtAll() {
        for (String endpoint : new String[] {
                "/actuator/env",
                "/actuator/configprops",
                "/actuator/beans",
                "/actuator/mappings",
                "/actuator/loggers",
                "/actuator/heapdump",
                "/actuator/threaddump",
                "/actuator/shutdown"
        }) {
            assertThat(get(endpoint).getStatusCode())
                    .as("management endpoint %s must not be reachable", endpoint)
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
