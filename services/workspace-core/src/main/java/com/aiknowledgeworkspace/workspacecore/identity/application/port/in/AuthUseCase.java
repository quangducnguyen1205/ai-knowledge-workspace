package com.aiknowledgeworkspace.workspacecore.identity.application.port.in;

import com.aiknowledgeworkspace.workspacecore.identity.application.command.LoginUserCommand;
import com.aiknowledgeworkspace.workspacecore.identity.application.command.RegisterUserCommand;
import com.aiknowledgeworkspace.workspacecore.identity.application.result.AuthenticatedUser;

public interface AuthUseCase {

    AuthenticatedUser register(RegisterUserCommand command);

    AuthenticatedUser login(LoginUserCommand command);

    AuthenticatedUser getUser(String authenticatedUserId);
}
