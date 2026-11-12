package com.example.keycloakroles.security;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApplicationPermission {

	ORDER_READ("order:read", "Read all demo orders"),
	ORDER_WRITE("order:write", "Create demo orders"),
	USER_READ("user:read", "Read the demo user directory"),
	AUDIT_READ("audit:read", "Read the audit demonstration");

	private static final Set<String> CODES = Arrays.stream(values())
		.map(ApplicationPermission::getCode).collect(Collectors.toUnmodifiableSet());

	private final String code;
	private final String description;

	public static boolean supports(String code) {
		return CODES.contains(code);
	}
}
