package com.aiknowledgeworkspace.workspacecore.common.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.workspace.infrastructure.persistence.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:oidc-user-provisioning;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "workspace.security.authentication-mode=keycloak_jwt",
        "workspace.security.oidc.issuer-uri=https://keycloak.example.test/realms/project3",
        "workspace.security.oidc.jwk-set-uri=http://localhost/not-used-in-this-test"
})
class OidcUserProvisioningServiceTest {

    private static final String ISSUER = "https://keycloak.example.test/realms/project3";

    @Autowired
    private OidcUserProvisioningService provisioningService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Test
    void firstJwtProvisionsLocalUserAndDefaultWorkspace() {
        UserAccount user = provisioningService.resolveUser(jwt("subject-1", "learner@example.com"));

        assertThat(user.getId()).isNotNull();
        assertThat(user.getEmail()).isEqualTo("learner@example.com");
        assertThat(user.getIdentityProvider()).isEqualTo(ISSUER);
        assertThat(user.getExternalSubject()).isEqualTo("subject-1");
        assertThat(user.getPasswordHash()).isEqualTo("{oidc-external}");
        assertThat(workspaceRepository.findAllByOwnerIdAndDefaultWorkspaceTrue(user.getId().toString()))
                .hasSize(1);
    }

    @Test
    void repeatedJwtResolvesSameLocalUser() {
        UserAccount first = provisioningService.resolveUser(jwt("subject-2", "repeat@example.com"));
        UserAccount second = provisioningService.resolveUser(jwt("subject-2", "changed@example.com"));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(userAccountRepository.findAll().stream()
                .filter(user -> ISSUER.equals(user.getIdentityProvider()))
                .filter(user -> "subject-2".equals(user.getExternalSubject())))
                .hasSize(1);
        assertThat(workspaceRepository.findAllByOwnerIdAndDefaultWorkspaceTrue(first.getId().toString()))
                .hasSize(1);
    }

    @Test
    void differentSubjectCreatesDifferentLocalUserEvenWhenEmailMatches() {
        UserAccount first = provisioningService.resolveUser(jwt("subject-3a", "shared@example.com"));
        UserAccount second = provisioningService.resolveUser(jwt("subject-3b", "shared@example.com"));

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(second.getExternalSubject()).isEqualTo("subject-3b");
        assertThat(second.getEmail()).isNotEqualTo("shared@example.com");
        assertThat(second.getEmail()).endsWith("@external.local");
    }

    @Test
    void concurrentFirstLoginDoesNotCreateDuplicateUsersOrDefaultWorkspaces() throws Exception {
        Jwt jwt = jwt("subject-concurrent", "concurrent@example.com");
        Callable<UUID> resolve = () -> provisioningService.resolveUser(jwt).getId();
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<UUID>> futures = executor.invokeAll(List.of(resolve, resolve, resolve, resolve));
            UUID resolvedId = futures.get(0).get();

            for (Future<UUID> future : futures) {
                assertThat(future.get()).isEqualTo(resolvedId);
            }
            assertThat(userAccountRepository.findAll().stream()
                    .filter(user -> ISSUER.equals(user.getIdentityProvider()))
                    .filter(user -> "subject-concurrent".equals(user.getExternalSubject())))
                    .hasSize(1);
            assertThat(workspaceRepository.findByOwnerId(resolvedId.toString(), Sort.unsorted()))
                    .hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private Jwt jwt(String subject, String email) {
        return new Jwt(
                "raw-access-token-value-must-not-be-stored",
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T01:00:00Z"),
                Map.of("alg", "none"),
                Map.of(
                        "iss", ISSUER,
                        "sub", subject,
                        "email", email
                )
        );
    }
}
