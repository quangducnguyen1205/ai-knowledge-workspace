package com.aiknowledgeworkspace.workspacecore.common.identity.infrastructure.persistence;

import com.aiknowledgeworkspace.workspacecore.common.identity.UserAccount;
import com.aiknowledgeworkspace.workspacecore.common.identity.application.UserAccountStore;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class UserAccountPersistenceAdapter implements UserAccountStore {

    private final UserAccountJpaRepository repository;

    UserAccountPersistenceAdapter(UserAccountJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<UserAccount> findById(UUID userId) {
        return repository.findById(userId);
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        return repository.findByEmail(email);
    }

    @Override
    public Optional<UserAccount> findByExternalIdentity(String identityProvider, String externalSubject) {
        return repository.findByIdentityProviderAndExternalSubject(identityProvider, externalSubject);
    }

    @Override
    public boolean emailExists(String email) {
        return repository.existsByEmail(email);
    }

    @Override
    public UserAccount save(UserAccount userAccount) {
        return repository.save(userAccount);
    }

    @Override
    public UserAccount saveAndFlush(UserAccount userAccount) {
        return repository.saveAndFlush(userAccount);
    }
}
