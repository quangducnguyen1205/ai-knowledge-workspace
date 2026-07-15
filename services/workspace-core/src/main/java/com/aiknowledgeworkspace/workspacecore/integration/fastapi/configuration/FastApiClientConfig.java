package com.aiknowledgeworkspace.workspacecore.integration.fastapi.configuration;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class FastApiClientConfig {

    @Bean("fastApiRestClient")
    RestClient fastApiRestClient(FastApiProperties properties) {
        return buildRestClient(properties.getBaseUrl(), properties.getConnectTimeout(), properties.getReadTimeout());
    }

    @Bean("fastApiAssistantRestClient")
    RestClient fastApiAssistantRestClient(FastApiProperties properties) {
        return buildRestClient(
                properties.getBaseUrl(), properties.getConnectTimeout(), properties.getAssistantReadTimeout()
        );
    }

    private RestClient buildRestClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.toIntExact(connectTimeout.toMillis()));
        requestFactory.setReadTimeout(Math.toIntExact(readTimeout.toMillis()));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
