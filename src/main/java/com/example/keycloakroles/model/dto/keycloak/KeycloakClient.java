package com.example.keycloakroles.model.dto.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakClient(String id, String clientId) {
}
