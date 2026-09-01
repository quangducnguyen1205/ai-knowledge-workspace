package com.aiknowledgeworkspace.workspacecore.integration.fastapi.adapter.out.provider.configuration;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class FastApiClientConfig {

    @Bean("fastApiRestClient")
    RestClient fastApiRestClient(FastApiProperties properties) {
        return buildRestClient(properties, properties.getReadTimeout());
    }

    @Bean("fastApiAssistantRestClient")
    RestClient fastApiAssistantRestClient(FastApiProperties properties) {
        return buildRestClient(properties, properties.getAssistantReadTimeout());
    }

    private RestClient buildRestClient(FastApiProperties properties, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.toIntExact(properties.getConnectTimeout().toMillis()));
        requestFactory.setReadTimeout(Math.toIntExact(readTimeout.toMillis()));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory);
        String internalToken = properties.getInternalToken();
        if (!internalToken.isBlank()) {
            // Sent only when configured: a blank token supports a processor deployment that
            // does not enforce service authentication.
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken);
        }
        return builder.build();
    }
}
