package com.aiknowledgeworkspace.workspacecore.integration.fastapi.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class FastApiClientConfigTest {

    @Test
    void assistantRestClientUsesAssistantSpecificReadTimeout() {
        FastApiProperties properties = spy(new FastApiProperties());
        properties.setConnectTimeout(Duration.ofSeconds(5));
        properties.setReadTimeout(Duration.ofSeconds(30));
        properties.setAssistantReadTimeout(Duration.ofSeconds(75));

        RestClient client = new FastApiClientConfig().fastApiAssistantRestClient(properties);

        assertThat(client).isNotNull();
        verify(properties).getConnectTimeout();
        verify(properties).getAssistantReadTimeout();
        verify(properties, never()).getReadTimeout();
    }
}
