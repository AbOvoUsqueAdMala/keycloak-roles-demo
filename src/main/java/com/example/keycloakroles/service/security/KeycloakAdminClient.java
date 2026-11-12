package com.example.keycloakroles.service.security;

import com.example.keycloakroles.config.KeycloakProperties;
import com.example.keycloakroles.config.RoleSyncProperties;
import com.example.keycloakroles.model.dto.keycloak.KeycloakClient;
import com.example.keycloakroles.model.dto.keycloak.KeycloakRole;
import com.example.keycloakroles.model.dto.keycloak.TokenResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
public class KeycloakAdminClient {

	private static final int PAGE_SIZE = 100;
	private static final String CLIENT_PATH = "/admin/realms/{realm}/clients/{clientId}";
	private static final String ROLE_PATH = CLIENT_PATH + "/roles/{roleName}";
	private final RestClient restClient;
	private final KeycloakProperties keycloakProperties;
	private final RoleSyncProperties roleSyncProperties;

	public String obtainAccessToken() {
		if (!StringUtils.hasText(roleSyncProperties.clientSecret())) {
			throw new IllegalStateException("KEYCLOAK_SYNC_CLIENT_SECRET is required for role sync");
		}
		LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "client_credentials");
		form.add("client_id", roleSyncProperties.clientId());
		form.add("client_secret", roleSyncProperties.clientSecret());
		TokenResponse response = restClient.post()
			.uri("/realms/{realm}/protocol/openid-connect/token", keycloakProperties.realm())
			.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
			.retrieve().body(TokenResponse.class);
		if (response == null || !StringUtils.hasText(response.accessToken())) {
			throw new IllegalStateException("Keycloak returned no service account access token");
		}
		return response.accessToken();
	}

	public String findResourceClientIdentifier(String accessToken) {
		KeycloakClient[] clients = restClient.get()
			.uri(builder -> builder.path("/admin/realms/{realm}/clients")
				.queryParam("clientId", keycloakProperties.resourceClientId())
				.build(keycloakProperties.realm()))
			.headers(headers -> headers.setBearerAuth(accessToken))
			.retrieve().body(KeycloakClient[].class);
		List<KeycloakClient> matches = Arrays.stream(Objects.requireNonNull(clients))
			.filter(client -> keycloakProperties.resourceClientId().equals(client.clientId())).toList();
		if (matches.size() != 1) {
			throw new IllegalStateException("Expected exactly one resource client: "
				+ keycloakProperties.resourceClientId());
		}
		return Objects.requireNonNull(matches.getFirst().id());
	}

	public Optional<KeycloakRole> findRole(String accessToken, String clientIdentifier, String name) {
		try {
			return Optional.ofNullable(restClient.get()
				.uri(ROLE_PATH, keycloakProperties.realm(), clientIdentifier, name)
				.headers(headers -> headers.setBearerAuth(accessToken))
				.retrieve().body(KeycloakRole.class));
		} catch (HttpClientErrorException.NotFound exception) {
			return Optional.empty();
		}
	}

	public void createRole(String accessToken, String clientIdentifier, KeycloakRole role) {
		restClient.post().uri(CLIENT_PATH + "/roles", keycloakProperties.realm(), clientIdentifier)
			.headers(headers -> headers.setBearerAuth(accessToken))
			.contentType(MediaType.APPLICATION_JSON).body(role).retrieve().toBodilessEntity();
	}

	public void updateRole(String accessToken, String clientIdentifier, KeycloakRole role) {
		restClient.put().uri(ROLE_PATH, keycloakProperties.realm(), clientIdentifier, role.name())
			.headers(headers -> headers.setBearerAuth(accessToken))
			.contentType(MediaType.APPLICATION_JSON).body(role).retrieve().toBodilessEntity();
	}

	public List<KeycloakRole> listComposites(String accessToken, String clientIdentifier, String name) {
		KeycloakRole[] roles = restClient.get()
			.uri(ROLE_PATH + "/composites", keycloakProperties.realm(), clientIdentifier, name)
			.headers(headers -> headers.setBearerAuth(accessToken))
			.retrieve().body(KeycloakRole[].class);
		return List.of(Objects.requireNonNull(roles));
	}

	public void addComposites(String accessToken, String clientIdentifier, String name,
		List<KeycloakRole> members) {
		changeComposites(HttpMethod.POST, accessToken, clientIdentifier, name, members);
	}

	public void removeComposites(String accessToken, String clientIdentifier, String name,
		List<KeycloakRole> members) {
		changeComposites(HttpMethod.DELETE, accessToken, clientIdentifier, name, members);
	}

	private void changeComposites(HttpMethod method, String accessToken, String clientIdentifier,
		String name, List<KeycloakRole> members) {
		if (members.isEmpty()) {
			return;
		}
		restClient.method(method)
			.uri(ROLE_PATH + "/composites", keycloakProperties.realm(), clientIdentifier, name)
			.headers(headers -> headers.setBearerAuth(accessToken))
			.contentType(MediaType.APPLICATION_JSON).body(members).retrieve().toBodilessEntity();
	}

	public List<KeycloakRole> listAllRoles(String accessToken, String clientIdentifier) {
		List<KeycloakRole> roles = new ArrayList<>();
		int offset = 0;
		while (true) {
			List<KeycloakRole> page = listRolePage(accessToken, clientIdentifier, offset);
			roles.addAll(page);
			if (page.size() < PAGE_SIZE) {
				return roles;
			}
			offset += PAGE_SIZE;
		}
	}

	private List<KeycloakRole> listRolePage(String accessToken, String clientIdentifier, int offset) {
		KeycloakRole[] roles = restClient.get()
			.uri(builder -> builder.path(CLIENT_PATH + "/roles")
				.queryParam("first", offset).queryParam("max", PAGE_SIZE)
				.queryParam("briefRepresentation", false)
				.build(keycloakProperties.realm(), clientIdentifier))
			.headers(headers -> headers.setBearerAuth(accessToken))
			.retrieve().body(KeycloakRole[].class);
		return List.of(Objects.requireNonNull(roles));
	}

	public void deleteRole(String accessToken, String clientIdentifier, String name) {
		try {
			restClient.delete().uri(ROLE_PATH, keycloakProperties.realm(), clientIdentifier, name)
				.headers(headers -> headers.setBearerAuth(accessToken)).retrieve().toBodilessEntity();
		} catch (HttpClientErrorException.NotFound exception) {
			// Another replica may already have removed this obsolete managed role.
		}
	}
}
