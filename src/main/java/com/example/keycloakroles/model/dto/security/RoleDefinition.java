package com.example.keycloakroles.model.dto.security;

import java.util.Set;

public record RoleDefinition(String name, String description, Set<String> members) {

	public RoleDefinition {
		members = Set.copyOf(members);
	}
}
