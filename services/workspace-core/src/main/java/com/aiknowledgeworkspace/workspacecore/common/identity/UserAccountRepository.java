package com.aiknowledgeworkspace.workspacecore.common.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findByIdentityProviderAndExternalSubject(String identityProvider, String externalSubject);

    boolean existsByEmail(String email);
}
