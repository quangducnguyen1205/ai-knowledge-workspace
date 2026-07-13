package com.aiknowledgeworkspace.workspacecore.common.identity;

import com.aiknowledgeworkspace.workspacecore.common.identity.provisioning.DefaultWorkspaceProvisioner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class TransactionalOidcUserCreationExecutor implements OidcUserCreationExecutor {

    private final UserAccountRepository userAccountRepository;
    private final DefaultWorkspaceProvisioner defaultWorkspaceProvisioner;

    TransactionalOidcUserCreationExecutor(
            UserAccountRepository userAccountRepository,
            DefaultWorkspaceProvisioner defaultWorkspaceProvisioner
    ) {
        this.userAccountRepository = userAccountRepository;
        this.defaultWorkspaceProvisioner = defaultWorkspaceProvisioner;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserAccount create(UserAccount userAccount) {
        UserAccount savedUser = userAccountRepository.saveAndFlush(userAccount);
        defaultWorkspaceProvisioner.provisionFor(savedUser.getId());
        return savedUser;
    }
}
