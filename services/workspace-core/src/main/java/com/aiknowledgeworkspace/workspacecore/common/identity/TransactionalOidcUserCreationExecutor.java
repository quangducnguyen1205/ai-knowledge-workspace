package com.aiknowledgeworkspace.workspacecore.common.identity;

import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceProperties;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class TransactionalOidcUserCreationExecutor implements OidcUserCreationExecutor {

    private final UserAccountRepository userAccountRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceProperties workspaceProperties;

    TransactionalOidcUserCreationExecutor(
            UserAccountRepository userAccountRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceProperties workspaceProperties
    ) {
        this.userAccountRepository = userAccountRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceProperties = workspaceProperties;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserAccount create(UserAccount userAccount) {
        UserAccount savedUser = userAccountRepository.saveAndFlush(userAccount);
        workspaceRepository.save(new Workspace(
                defaultWorkspaceIdFor(savedUser.getId()),
                workspaceProperties.getDefaultName(),
                savedUser.getId().toString(),
                true
        ));
        return savedUser;
    }

    private UUID defaultWorkspaceIdFor(UUID userId) {
        return UUID.nameUUIDFromBytes(("default-workspace:" + userId).getBytes(StandardCharsets.UTF_8));
    }
}
