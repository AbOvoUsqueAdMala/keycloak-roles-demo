package com.example.keycloakroles.model.dto.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TokenResponse(@JsonProperty("access_token") String accessToken) {

	@Override
	public String toString() {
		return "TokenResponse[accessToken=<redacted>]";
	}
}
