package com.aiknowledgeworkspace.workspacecore.identity.application.service;

import com.aiknowledgeworkspace.workspacecore.identity.domain.UserAccount;
import com.aiknowledgeworkspace.workspacecore.identity.application.port.out.workspace.DefaultWorkspaceProvisioner;
import com.aiknowledgeworkspace.workspacecore.identity.application.port.out.UserAccountStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class TransactionalOidcUserCreationExecutor implements OidcUserCreationExecutor {

    private final UserAccountStore userAccountStore;
    private final DefaultWorkspaceProvisioner defaultWorkspaceProvisioner;

    TransactionalOidcUserCreationExecutor(
            UserAccountStore userAccountStore,
            DefaultWorkspaceProvisioner defaultWorkspaceProvisioner
    ) {
        this.userAccountStore = userAccountStore;
        this.defaultWorkspaceProvisioner = defaultWorkspaceProvisioner;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserAccount create(UserAccount userAccount) {
        UserAccount savedUser = userAccountStore.saveAndFlush(userAccount);
        defaultWorkspaceProvisioner.provisionFor(savedUser.getId());
        return savedUser;
    }
}
