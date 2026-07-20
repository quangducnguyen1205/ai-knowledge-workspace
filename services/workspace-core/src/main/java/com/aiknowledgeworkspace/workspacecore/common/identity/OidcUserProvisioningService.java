package com.aiknowledgeworkspace.workspacecore.common.identity;

import java.nio.charset.StandardCharsets;
import com.aiknowledgeworkspace.workspacecore.common.identity.application.UserAccountStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OidcUserProvisioningService {

    private static final String OIDC_PASSWORD_MARKER = "{oidc-external}";
    private static final String LOCAL_EMAIL_DOMAIN = "external.local";

    private final UserAccountStore userAccountStore;
    private final OidcJwtIdentityMapper identityMapper;
    private final OidcUserCreationExecutor creationExecutor;

    public OidcUserProvisioningService(
            UserAccountStore userAccountStore,
            OidcJwtIdentityMapper identityMapper,
            OidcUserCreationExecutor creationExecutor
    ) {
        this.userAccountStore = userAccountStore;
        this.identityMapper = identityMapper;
        this.creationExecutor = creationExecutor;
    }

    @Transactional(readOnly = true)
    public UserAccount resolveUser(Jwt jwt) {
        OidcJwtIdentity identity = identityMapper.toIdentity(jwt);
        return userAccountStore
                .findByExternalIdentity(identity.identityProvider(), identity.externalSubject())
                .orElseGet(() -> createOrFindUser(identity));
    }

    private UserAccount createOrFindUser(OidcJwtIdentity identity) {
        try {
            return creationExecutor.create(UserAccount.oidcUser(
                    chooseProductEmail(identity),
                    OIDC_PASSWORD_MARKER,
                    identity.identityProvider(),
                    identity.externalSubject()
            ));
        } catch (DataIntegrityViolationException exception) {
            return userAccountStore
                    .findByExternalIdentity(identity.identityProvider(), identity.externalSubject())
                    .orElseThrow(() -> exception);
        }
    }

    private String chooseProductEmail(OidcJwtIdentity identity) {
        if (StringUtils.hasText(identity.email()) && !userAccountStore.emailExists(identity.email())) {
            return identity.email();
        }

        return "oidc-" + stableIdentityHash(identity).substring(0, 32) + "@" + LOCAL_EMAIL_DOMAIN;
    }

    private String stableIdentityHash(OidcJwtIdentity identity) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((identity.identityProvider() + "\0" + identity.externalSubject())
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is required", exception);
        }
    }
}
