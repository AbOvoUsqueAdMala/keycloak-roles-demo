package com.example.keycloakroles.security;

import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApplicationRole {

	READER("Read-only access", Set.of(ApplicationPermission.ORDER_READ)),
	MANAGER("Manage orders", Set.of(
		ApplicationPermission.ORDER_READ, ApplicationPermission.ORDER_WRITE)),
	ADMIN("Administer the demo", Set.of(
		ApplicationPermission.ORDER_READ, ApplicationPermission.ORDER_WRITE,
		ApplicationPermission.USER_READ));

	private final String description;
	private final Set<ApplicationPermission> permissions;
}
