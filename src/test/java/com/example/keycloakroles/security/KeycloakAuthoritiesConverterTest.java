package com.example.keycloakroles.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakAuthoritiesConverterTest {

	private final KeycloakAuthoritiesConverter converter = new KeycloakAuthoritiesConverter("orders-api");

	@Test
	void mapsOnlyRolesOfTheIntendedClientAndKeepsPermissionNames() {
		Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "RS256").subject("user-1")
			.claim("realm_access", Map.of("roles", List.of("ADMIN")))
			.claim("resource_access", Map.of(
				"orders-api", Map.of("roles", List.of("READER", "order:read", "READER")),
				"another-api", Map.of("roles", List.of("ADMIN", "user:read"))))
			.build();

		assertThat(converter.convert(jwt)).extracting(GrantedAuthority::getAuthority)
			.containsExactlyInAnyOrder("ROLE_READER", "order:read");
	}

	@Test
	void absenceOfClientRolesDoesNotGrantAnyAuthority() {
		Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "RS256").subject("user-1").build();

		assertThat(converter.convert(jwt)).isEmpty();
	}
}
