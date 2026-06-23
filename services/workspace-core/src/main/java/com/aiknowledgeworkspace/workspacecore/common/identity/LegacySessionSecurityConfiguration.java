package com.aiknowledgeworkspace.workspacecore.common.identity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnProperty(
        prefix = "workspace.security",
        name = "authentication-mode",
        havingValue = "legacy_session",
        matchIfMissing = true
)
class LegacySessionSecurityConfiguration {

    @Bean
    SecurityFilterChain legacySessionSecurityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }
}
