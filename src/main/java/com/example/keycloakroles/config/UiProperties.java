package com.example.keycloakroles.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.ui")
public record UiProperties(@NotBlank String clientId, String clientSecret) {

	@Override
	public String toString() {
		return "UiProperties[clientId=" + clientId + ", clientSecret=<redacted>]";
	}
}
