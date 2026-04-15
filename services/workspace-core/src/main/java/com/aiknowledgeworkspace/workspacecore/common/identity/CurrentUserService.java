package com.aiknowledgeworkspace.workspacecore.common.identity;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class CurrentUserService {

    private final CurrentUserProperties currentUserProperties;

    public CurrentUserService(CurrentUserProperties currentUserProperties) {
        this.currentUserProperties = currentUserProperties;
    }

    public String getCurrentUserId() {
        ServletRequestAttributes requestAttributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return currentUserProperties.getDefaultId();
        }

        String requestUserId = requestAttributes.getRequest().getHeader(currentUserProperties.getHeaderName());
        if (StringUtils.hasText(requestUserId)) {
            return requestUserId.trim();
        }

        return currentUserProperties.getDefaultId();
    }

    public boolean isDefaultUser(String userId) {
        return currentUserProperties.getDefaultId().equals(userId);
    }

    public String getHeaderName() {
        return currentUserProperties.getHeaderName();
    }
}
