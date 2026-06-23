package com.aiknowledgeworkspace.workspacecore.common.identity;

@FunctionalInterface
public interface OidcUserCreationExecutor {

    UserAccount create(UserAccount userAccount);
}
