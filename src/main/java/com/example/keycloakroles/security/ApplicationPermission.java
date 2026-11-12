package com.example.keycloakroles.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApplicationPermission {

	ORDER_READ("order:read", "Read all demo orders"),
	ORDER_WRITE("order:write", "Create demo orders"),
	USER_READ("user:read", "Read the demo user directory");

	private final String code;
	private final String description;
}
