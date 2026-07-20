package com.aiknowledgeworkspace.workspacecore.identity.adapter.out.persistence;

import com.aiknowledgeworkspace.workspacecore.identity.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserAccountJpaRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findByIdentityProviderAndExternalSubject(String identityProvider, String externalSubject);

    boolean existsByEmail(String email);
}
