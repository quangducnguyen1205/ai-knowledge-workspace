package com.aiknowledgeworkspace.workspacecore.common.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KeycloakRealmImportValidationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Path REALM_IMPORT = findRepositoryRoot()
        .resolve("infra/keycloak/realm-import/workspace-dev-realm.json");

    @Test
    void realmImportDefinesLocalPublicPkceClientWithoutCredentials() throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(REALM_IMPORT.toFile());

        assertThat(root.path("realm").asText()).isEqualTo("workspace-dev");
        assertThat(root.path("enabled").asBoolean()).isTrue();
        assertThat(root.has("users")).isFalse();
        assertNoSensitiveCredentialFields(root);

        JsonNode client = findClient(root, "workspace-web");
        assertThat(client).isNotNull();
        assertThat(client.path("protocol").asText()).isEqualTo("openid-connect");
        assertThat(client.path("publicClient").asBoolean()).isTrue();
        assertThat(client.path("standardFlowEnabled").asBoolean()).isTrue();
        assertThat(client.path("directAccessGrantsEnabled").asBoolean()).isFalse();
        assertThat(client.path("serviceAccountsEnabled").asBoolean()).isFalse();
        assertThat(client.path("attributes").path("pkce.code.challenge.method").asText()).isEqualTo("S256");

        assertLocalhostOnly(client.path("redirectUris"));
        assertLocalhostOnly(client.path("webOrigins"));
        assertWorkspaceCoreAudienceMapper(client);
    }

    private static JsonNode findClient(JsonNode root, String clientId) {
        for (JsonNode client : root.path("clients")) {
            if (clientId.equals(client.path("clientId").asText())) {
                return client;
            }
        }
        return null;
    }

    private static void assertLocalhostOnly(JsonNode uris) {
        assertThat(uris).isNotEmpty();
        for (JsonNode uri : uris) {
            String value = uri.asText();
            assertThat(value)
                .startsWith("http://");
            assertThat(value)
                .satisfiesAnyOf(
                    candidate -> assertThat(candidate).startsWith("http://localhost:5173"),
                    candidate -> assertThat(candidate).startsWith("http://127.0.0.1:5173")
                );
        }
    }

    private static void assertWorkspaceCoreAudienceMapper(JsonNode client) {
        JsonNode mapper = null;
        for (JsonNode protocolMapper : client.path("protocolMappers")) {
            if ("oidc-audience-mapper".equals(protocolMapper.path("protocolMapper").asText())) {
                mapper = protocolMapper;
                break;
            }
        }

        assertThat(mapper).isNotNull();
        assertThat(mapper.path("protocol").asText()).isEqualTo("openid-connect");
        assertThat(mapper.path("config").path("included.custom.audience").asText()).isEqualTo("workspace-core");
        assertThat(mapper.path("config").path("access.token.claim").asText()).isEqualTo("true");
        assertThat(mapper.path("config").path("id.token.claim").asText()).isEqualTo("false");
    }

    private static void assertNoSensitiveCredentialFields(JsonNode node) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                assertThat(field.getKey())
                    .isNotIn("users", "password", "secret", "token", "credential", "credentials", "clientSecret");
                assertNoSensitiveCredentialFields(field.getValue());
            }
            return;
        }

        if (node.isArray()) {
            node.forEach(KeycloakRealmImportValidationTest::assertNoSensitiveCredentialFields);
        }
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("infra/docker-compose.dev.yml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root from " + Path.of("").toAbsolutePath());
    }
}
