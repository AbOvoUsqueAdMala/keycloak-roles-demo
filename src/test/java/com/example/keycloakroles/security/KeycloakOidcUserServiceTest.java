package com.example.keycloakroles.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class KeycloakOidcUserServiceTest {

	private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);
	private final KeycloakOidcUserService userService = new KeycloakOidcUserService(
		jwtDecoder, new KeycloakAuthoritiesConverter("orders-api"));

	@Test
	void mapsRolesFromTheValidatedAccessToken() {
		Jwt accessToken = Jwt.withTokenValue("access-token").header("alg", "RS256").subject("user-1")
			.claim("resource_access", Map.of("orders-api", Map.of("roles", List.of("READER", "order:read"))))
			.build();
		when(jwtDecoder.decode("access-token")).thenReturn(accessToken);

		assertThat(userService.loadUser(userRequest()).getAuthorities())
			.extracting(GrantedAuthority::getAuthority).contains("ROLE_READER", "order:read")
			.doesNotContain("ROLE_ADMIN");
	}

	@Test
	void rejectsRolesFromAnAccessTokenBelongingToAnotherUser() {
		Jwt accessToken = Jwt.withTokenValue("access-token").header("alg", "RS256")
			.subject("another-user").build();
		when(jwtDecoder.decode("access-token")).thenReturn(accessToken);

		assertThatThrownBy(() -> userService.loadUser(userRequest()))
			.isInstanceOf(OAuth2AuthenticationException.class);
	}

	private OidcUserRequest userRequest() {
		ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
			.clientId("orders-ui").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri("http://localhost/login/oauth2/code/keycloak")
			.authorizationUri("https://keycloak.test/auth").tokenUri("https://keycloak.test/token")
			.jwkSetUri("https://keycloak.test/certs").build();
		Instant issuedAt = Instant.now();
		OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
			"access-token", issuedAt, issuedAt.plusSeconds(120));
		OidcIdToken idToken = OidcIdToken.withTokenValue("id-token").subject("user-1")
			.issuedAt(issuedAt).expiresAt(issuedAt.plusSeconds(120))
			.claim("resource_access", Map.of("orders-api", Map.of("roles", List.of("ADMIN")))).build();
		return new OidcUserRequest(registration, accessToken, idToken);
	}
}
