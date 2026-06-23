package com.aiknowledgeworkspace.workspacecore.common.identity;

import java.net.URL;
import java.util.Locale;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OidcJwtIdentityMapper {

    static final int MAX_IDENTITY_VALUE_LENGTH = 255;

    private final WorkspaceSecurityProperties securityProperties;

    public OidcJwtIdentityMapper(WorkspaceSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public OidcJwtIdentity toIdentity(Jwt jwt) {
        if (jwt == null) {
            throw invalidIdentity();
        }

        String subject = normalizeRequiredClaim(jwt.getSubject(), "sub");
        String provider = normalizeIssuer(jwt.getIssuer());
        validateConfiguredIssuer(provider);
        validateConfiguredAudience(jwt);
        String email = normalizeOptionalEmail(jwt.getClaimAsString("email"));

        return new OidcJwtIdentity(provider, subject, email);
    }

    private String normalizeIssuer(URL issuer) {
        if (issuer == null || !StringUtils.hasText(issuer.toString())) {
            throw invalidIdentity();
        }
        return normalizeRequiredClaim(issuer.toString(), "iss");
    }

    private String normalizeRequiredClaim(String value, String claimName) {
        if (!StringUtils.hasText(value)) {
            throw new InvalidJwtIdentityException("JWT " + claimName + " claim is required");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_IDENTITY_VALUE_LENGTH) {
            throw new InvalidJwtIdentityException("JWT " + claimName + " claim is too long");
        }
        return normalized;
    }

    private void validateConfiguredIssuer(String tokenIssuer) {
        String expectedIssuer = securityProperties.getOidc().getIssuerUri();
        if (!StringUtils.hasText(expectedIssuer)) {
            throw new InvalidJwtIdentityException("OIDC issuer URI is required in keycloak_jwt mode");
        }
        if (!tokenIssuer.equals(expectedIssuer.trim())) {
            throw invalidIdentity();
        }
    }

    private void validateConfiguredAudience(Jwt jwt) {
        String expectedAudience = securityProperties.getOidc().getAudience();
        if (!StringUtils.hasText(expectedAudience)) {
            return;
        }
        String normalizedAudience = expectedAudience.trim();
        if (!jwt.getAudience().contains(normalizedAudience)) {
            throw invalidIdentity();
        }
    }

    private String normalizeOptionalEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_IDENTITY_VALUE_LENGTH || !looksLikeEmail(normalized)) {
            return null;
        }
        return normalized;
    }

    private boolean looksLikeEmail(String email) {
        int atIndex = email.indexOf('@');
        return atIndex > 0
                && atIndex == email.lastIndexOf('@')
                && atIndex < email.length() - 1;
    }

    private InvalidJwtIdentityException invalidIdentity() {
        return new InvalidJwtIdentityException("JWT identity could not be resolved");
    }
}
