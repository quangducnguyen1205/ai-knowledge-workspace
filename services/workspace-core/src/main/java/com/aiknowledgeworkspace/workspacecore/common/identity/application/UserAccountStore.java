package com.aiknowledgeworkspace.workspacecore.common.identity.application;

import com.aiknowledgeworkspace.workspacecore.common.identity.UserAccount;
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
