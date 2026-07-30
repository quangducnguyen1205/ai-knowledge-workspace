package com.aiknowledgeworkspace.workspacecore;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Refuses to start a production-like runtime that still resolves an anonymous request to a local
 * development user.
 *
 * <p>The `production-like` profile already sets {@code current-user.dev-fallback-enabled=false},
 * but a profile default can be overridden by an environment variable. Making the contradiction a
 * startup failure means the invariant cannot be lost silently: either the deployment is explicitly
 * local development, or missing authentication is rejected with 401.
 */
@Component
@Profile("production-like")
public class ProductionLikeAuthenticationProfileValidator implements InitializingBean {

    private final boolean devFallbackEnabled;

    public ProductionLikeAuthenticationProfileValidator(
            @Value("${current-user.dev-fallback-enabled:true}") boolean devFallbackEnabled
    ) {
        this.devFallbackEnabled = devFallbackEnabled;
    }

    @Override
    public void afterPropertiesSet() {
        if (devFallbackEnabled) {
            throw new IllegalStateException(
                    "Refusing to start: the production-like profile is active while the development "
                            + "authentication fallback is enabled. Unset "
                            + "CURRENT_USER_DEV_FALLBACK_ENABLED or set it to false."
            );
        }
    }
}
