package com.aiknowledgeworkspace.workspacecore.common.identity;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(
        prefix = "workspace.security",
        name = "authentication-mode",
        havingValue = "keycloak_jwt"
)
class KeycloakJwtSecurityConfiguration {

    @Bean
    SecurityFilterChain keycloakJwtSecurityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/health").permitAll()
                        .requestMatchers(
                                "/api/auth/session",
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/logout"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> {
                }))
                .build();
    }

    @Bean
    JwtDecoder workspaceJwtDecoder(WorkspaceSecurityProperties securityProperties) {
        WorkspaceSecurityProperties.Oidc oidc = securityProperties.getOidc();
        String issuerUri = normalizeRequired(oidc.getIssuerUri(), "workspace.security.oidc.issuer-uri");

        NimbusJwtDecoder jwtDecoder;
        if (StringUtils.hasText(oidc.getJwkSetUri())) {
            jwtDecoder = NimbusJwtDecoder.withJwkSetUri(oidc.getJwkSetUri().trim()).build();
        } else {
            JwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuerUri);
            if (!(decoder instanceof NimbusJwtDecoder nimbusJwtDecoder)) {
                return decoder;
            }
            jwtDecoder = nimbusJwtDecoder;
        }

        jwtDecoder.setJwtValidator(jwtValidator(issuerUri, oidc.getAudience()));
        return jwtDecoder;
    }

    private OAuth2TokenValidator<Jwt> jwtValidator(String issuerUri, String audience) {
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> timestampValidator = new JwtTimestampValidator(Duration.ofSeconds(60));
        if (!StringUtils.hasText(audience)) {
            return new DelegatingOAuth2TokenValidator<>(issuerValidator, timestampValidator);
        }

        return new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                timestampValidator,
                audienceValidator(audience.trim())
        );
    }

    private OAuth2TokenValidator<Jwt> audienceValidator(String expectedAudience) {
        return jwt -> {
            if (jwt.getAudience().contains(expectedAudience)) {
                return OAuth2TokenValidatorResult.success();
            }

            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token",
                    "Required audience is missing",
                    null
            ));
        };
    }

    private String normalizeRequired(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(propertyName + " is required when workspace.security.authentication-mode=keycloak_jwt");
        }
        return value.trim();
    }
}
