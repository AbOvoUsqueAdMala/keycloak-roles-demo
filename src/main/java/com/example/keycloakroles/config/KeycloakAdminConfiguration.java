package com.example.keycloakroles.config;

import com.example.keycloakroles.service.security.KeycloakAdminClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.role-sync.enabled", havingValue = "true")
public class KeycloakAdminConfiguration {

	@Bean
	KeycloakAdminClient keycloakAdminClient(KeycloakProperties keycloakProperties,
		RoleSyncProperties roleSyncProperties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(2));
		requestFactory.setReadTimeout(Duration.ofSeconds(5));
		RestClient restClient = RestClient.builder()
			.baseUrl(keycloakProperties.adminUrl().toString())
			.requestFactory(requestFactory).build();
		return new KeycloakAdminClient(restClient, keycloakProperties, roleSyncProperties);
	}
}
