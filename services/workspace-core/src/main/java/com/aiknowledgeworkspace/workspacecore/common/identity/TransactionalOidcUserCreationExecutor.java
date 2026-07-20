package com.aiknowledgeworkspace.workspacecore.common.identity;

import com.aiknowledgeworkspace.workspacecore.common.identity.provisioning.DefaultWorkspaceProvisioner;
import com.aiknowledgeworkspace.workspacecore.common.identity.application.UserAccountStore;
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
