package com.example.keycloakroles.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.role-sync")
public record RoleSyncProperties(
	boolean enabled,
	boolean prune,
	@NotBlank String clientId,
	String clientSecret) {

	@Override
	public String toString() {
		return "RoleSyncProperties[enabled=" + enabled + ", prune=" + prune
			+ ", clientId=" + clientId + ", clientSecret=<redacted>]";
	}
}
