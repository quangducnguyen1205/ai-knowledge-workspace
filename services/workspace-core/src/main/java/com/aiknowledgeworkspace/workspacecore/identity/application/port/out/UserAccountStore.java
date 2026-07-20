package com.aiknowledgeworkspace.workspacecore.identity.application.port.out;

import com.aiknowledgeworkspace.workspacecore.identity.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountStore {

    Optional<UserAccount> findById(UUID userId);

    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findByExternalIdentity(String identityProvider, String externalSubject);

    boolean emailExists(String email);

    UserAccount save(UserAccount userAccount);

    UserAccount saveAndFlush(UserAccount userAccount);
}
