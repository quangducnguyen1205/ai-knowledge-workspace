package com.aiknowledgeworkspace.workspacecore.identity.application.service;

import com.aiknowledgeworkspace.workspacecore.identity.domain.UserAccount;

@FunctionalInterface
public interface OidcUserCreationExecutor {

    UserAccount create(UserAccount userAccount);
}
