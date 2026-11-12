package com.example.keycloakroles.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.keycloak")
public record KeycloakProperties(
	@NotNull URI issuerUri,
	@NotNull URI jwkSetUri,
	@NotNull URI internalUrl,
	@NotBlank String realm,
	@NotBlank String resourceClientId) {
}
