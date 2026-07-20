package com.aiknowledgeworkspace.workspacecore.identity.api;

/** Identity facts exposed to product application modules. */
public interface CurrentUserContext {
    String getCurrentUserId();

    boolean isDefaultUser(String userId);
}
