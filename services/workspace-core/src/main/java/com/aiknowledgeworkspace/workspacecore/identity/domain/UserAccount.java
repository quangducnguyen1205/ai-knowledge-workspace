package com.aiknowledgeworkspace.workspacecore.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_accounts")
public class UserAccount {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "identity_provider", length = 255)
    private String identityProvider;

    @Column(name = "external_subject", length = 255)
    private String externalSubject;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected UserAccount() {
    }

    public UserAccount(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public static UserAccount oidcUser(String email, String passwordHash, String identityProvider, String externalSubject) {
        UserAccount userAccount = new UserAccount(email, passwordHash);
        userAccount.identityProvider = identityProvider;
        userAccount.externalSubject = externalSubject;
        return userAccount;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getIdentityProvider() {
        return identityProvider;
    }

    public String getExternalSubject() {
        return externalSubject;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
