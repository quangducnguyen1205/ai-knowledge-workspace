package com.aiknowledgeworkspace.workspacecore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiknowledgeworkspace.workspacecore.common.web.adapter.in.web.BuildIdentityController;
import java.time.Instant;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Deployment-facing invariants for Phase 10: the running revision can be identified safely, and a
 * production-like runtime never resolves an anonymous request to a local development user.
 */
class DeploymentHardeningTest {

    // ------------------------------------------------------------ build identity

    private static MockMvc buildInfoMockMvc(BuildProperties buildProperties) {
        @SuppressWarnings("unchecked")
        ObjectProvider<BuildProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(buildProperties);
        return MockMvcBuilders
                .standaloneSetup(new BuildIdentityController("workspace-core", provider))
                .build();
    }

    private static BuildProperties buildProperties(String version, String commit, Instant time) {
        Properties properties = new Properties();
        if (version != null) {
            properties.put("version", version);
        }
        if (commit != null) {
            properties.put("commit", commit);
        }
        if (time != null) {
            properties.put("time", String.valueOf(time.toEpochMilli()));
        }
        return new BuildProperties(properties);
    }

    @Test
    void buildIdentityReportsTheRevisionWhenTheBuildSuppliedIt() throws Exception {
        MockMvc mockMvc = buildInfoMockMvc(buildProperties(
                "0.0.1-SNAPSHOT", "f16d8c14451755181f7ed774d6262743e7a6773c",
                Instant.parse("2026-07-30T10:00:00Z")));

        mockMvc.perform(get("/api/build-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("workspace-core"))
                .andExpect(jsonPath("$.version").value("0.0.1-SNAPSHOT"))
                .andExpect(jsonPath("$.gitCommit").value("f16d8c14451755181f7ed774d6262743e7a6773c"))
                .andExpect(jsonPath("$.buildTime").value("2026-07-30T10:00:00Z"));
    }

    @Test
    void buildIdentityDegradesSafelyWhenTheBuildDidNotGenerateMetadata() throws Exception {
        MockMvc mockMvc = buildInfoMockMvc(null);

        mockMvc.perform(get("/api/build-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("workspace-core"))
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.gitCommit").doesNotExist())
                .andExpect(jsonPath("$.buildTime").doesNotExist());
    }

    @Test
    void anUnresolvedCommitIsReportedAsAbsentRatherThanAsAPlaceholder() throws Exception {
        MockMvc mockMvc = buildInfoMockMvc(buildProperties("0.0.1-SNAPSHOT", "unknown", null));

        mockMvc.perform(get("/api/build-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("0.0.1-SNAPSHOT"))
                .andExpect(jsonPath("$.gitCommit").doesNotExist())
                .andExpect(jsonPath("$.buildTime").doesNotExist());
    }

    @Test
    void buildIdentityNeverExposesPathsSecretsUsersOrDependencies() throws Exception {
        Properties properties = new Properties();
        properties.put("version", "0.0.1-SNAPSHOT");
        properties.put("commit", "f16d8c1");
        properties.put("time", "1785500000000");
        // Fields a careless build might add; the bounded contract must not surface them.
        properties.put("artifact", "workspace-core");
        properties.put("group", "com.aiknowledgeworkspace");
        properties.put("java.home", "/Users/someone/.sdkman/candidates/java");
        properties.put("user.name", "someone");
        properties.put("db.password", "super-secret");
        MockMvc mockMvc = buildInfoMockMvc(new BuildProperties(properties));

        String body = mockMvc.perform(get("/api/build-info"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("application", "version", "gitCommit", "buildTime");
        assertThat(body).doesNotContain(
                "/Users/", "user.name", "someone", "super-secret", "db.password", "java.home",
                "group", "artifact", "dependencies", "classpath");
    }

    @Test
    void theBuildIdentityContractIsExactlyFourBoundedFields() {
        assertThat(com.aiknowledgeworkspace.workspacecore.common.web.api.BuildIdentity.class
                .getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("application", "version", "gitCommit", "buildTime");
    }

    // -------------------------------------------------- authentication hardening

    @Test
    void aProductionLikeRuntimeRefusesToStartWithTheDevelopmentFallbackEnabled() {
        ProductionLikeAuthenticationProfileValidator validator =
                new ProductionLikeAuthenticationProfileValidator(true);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("production-like")
                .hasMessageContaining("CURRENT_USER_DEV_FALLBACK_ENABLED");
    }

    @Test
    void aProductionLikeRuntimeStartsWhenTheDevelopmentFallbackIsDisabled() {
        ProductionLikeAuthenticationProfileValidator validator =
                new ProductionLikeAuthenticationProfileValidator(false);

        validator.afterPropertiesSet();
    }

    @Test
    void theValidatorIsBoundToTheProductionLikeProfileOnly() {
        org.springframework.context.annotation.Profile profile =
                ProductionLikeAuthenticationProfileValidator.class
                        .getAnnotation(org.springframework.context.annotation.Profile.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("production-like");
    }

    @Test
    void theStartupFailureNeverLeaksAnIdentityOrACredential() {
        ProductionLikeAuthenticationProfileValidator validator =
                new ProductionLikeAuthenticationProfileValidator(true);

        assertThatThrownBy(validator::afterPropertiesSet)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .doesNotContain("local-dev-user")
                        .doesNotContain("password")
                        .doesNotContain("token")
                        .doesNotContain("@"));
    }
}
