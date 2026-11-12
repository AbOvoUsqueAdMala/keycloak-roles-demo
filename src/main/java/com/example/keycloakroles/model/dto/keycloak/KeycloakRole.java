package com.example.keycloakroles.model.dto.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakRole(
	String id, String name, String description, Map<String, List<String>> attributes) {

	public KeycloakRole {
		attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
	}
}
