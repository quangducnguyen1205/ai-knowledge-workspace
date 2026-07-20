package com.aiknowledgeworkspace.workspacecore.identity.adapter.out.persistence;

import com.aiknowledgeworkspace.workspacecore.identity.domain.UserAccount;
import com.aiknowledgeworkspace.workspacecore.identity.application.port.out.UserAccountStore;
import com.aiknowledgeworkspace.workspacecore.identity.application.port.out.UserAccountConflictException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
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
        try {
            return repository.save(userAccount);
        } catch (DataIntegrityViolationException exception) {
            throw new UserAccountConflictException(exception);
        }
    }

    @Override
    public UserAccount saveAndFlush(UserAccount userAccount) {
        try {
            return repository.saveAndFlush(userAccount);
        } catch (DataIntegrityViolationException exception) {
            throw new UserAccountConflictException(exception);
        }
    }
}
