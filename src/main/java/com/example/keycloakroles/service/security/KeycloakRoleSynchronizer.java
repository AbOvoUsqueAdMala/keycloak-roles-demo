package com.example.keycloakroles.service.security;

import com.example.keycloakroles.config.KeycloakProperties;
import com.example.keycloakroles.config.RoleSyncProperties;
import com.example.keycloakroles.model.dto.keycloak.KeycloakRole;
import com.example.keycloakroles.model.dto.security.RoleDefinition;
import com.example.keycloakroles.security.RoleCatalog;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.role-sync.enabled", havingValue = "true")
public class KeycloakRoleSynchronizer {

	static final String OWNER_ATTRIBUTE = "managed-by";
	private final KeycloakAdminClient adminClient;
	private final KeycloakProperties keycloakProperties;
	private final RoleSyncProperties roleSyncProperties;
	private final RoleCatalog roleCatalog;

	public void synchronize() {
		String accessToken = adminClient.obtainAccessToken();
		String clientIdentifier = adminClient.findResourceClientIdentifier(accessToken);
		List<RoleDefinition> definitions = roleCatalog.definitions();
		Map<String, KeycloakRole> roles = new LinkedHashMap<>();
		for (RoleDefinition definition : definitions) {
			roles.put(definition.name(), synchronizeMetadata(accessToken, clientIdentifier, definition));
		}
		for (RoleDefinition definition : definitions) {
			synchronizeComposites(accessToken, clientIdentifier, definition, roles);
		}
		if (roleSyncProperties.prune()) {
			deleteObsoleteManagedRoles(accessToken, clientIdentifier, roles.keySet());
		}
		log.info("Synchronized {} client roles for {}", roles.size(),
			keycloakProperties.resourceClientId());
	}

	private KeycloakRole synchronizeMetadata(String accessToken, String clientIdentifier,
		RoleDefinition definition) {
		KeycloakRole existing = adminClient.findRole(accessToken, clientIdentifier, definition.name())
			.orElse(null);
		if (existing == null) {
			createIfAbsent(accessToken, clientIdentifier, definition);
			existing = adminClient.findRole(accessToken, clientIdentifier, definition.name())
				.orElseThrow(() -> new IllegalStateException("Created role not found: " + definition.name()));
		}
		Map<String, List<String>> attributes = new HashMap<>(existing.attributes());
		List<String> owner = List.of(keycloakProperties.resourceClientId());
		if (attributes.containsKey(OWNER_ATTRIBUTE) && !owner.equals(attributes.get(OWNER_ATTRIBUTE))) {
			throw new IllegalStateException("Role belongs to another manager: " + definition.name());
		}
		attributes.put(OWNER_ATTRIBUTE, owner);
		KeycloakRole desired = new KeycloakRole(existing.id(), definition.name(),
			definition.description(), attributes);
		if (!desired.equals(existing)) {
			adminClient.updateRole(accessToken, clientIdentifier, desired);
		}
		return desired;
	}

	private void createIfAbsent(String accessToken, String clientIdentifier, RoleDefinition definition) {
		KeycloakRole role = new KeycloakRole(null, definition.name(), definition.description(),
			Map.of(OWNER_ATTRIBUTE, List.of(keycloakProperties.resourceClientId())));
		try {
			adminClient.createRole(accessToken, clientIdentifier, role);
		} catch (HttpClientErrorException.Conflict exception) {
			// A same-version replica may have created the role between GET and POST.
		}
	}

	private void synchronizeComposites(String accessToken, String clientIdentifier,
		RoleDefinition definition, Map<String, KeycloakRole> roles) {
		List<KeycloakRole> current = adminClient.listComposites(
			accessToken, clientIdentifier, definition.name());
		List<KeycloakRole> desired = definition.members().stream().sorted()
			.map(name -> Objects.requireNonNull(roles.get(name), "Unknown member: " + name)).toList();
		Set<String> currentIdentifiers = identifiers(current);
		Set<String> desiredIdentifiers = identifiers(desired);
		List<KeycloakRole> removed = current.stream()
			.filter(role -> !desiredIdentifiers.contains(role.id())).toList();
		List<KeycloakRole> added = desired.stream()
			.filter(role -> !currentIdentifiers.contains(role.id())).toList();
		adminClient.removeComposites(accessToken, clientIdentifier, definition.name(), removed);
		adminClient.addComposites(accessToken, clientIdentifier, definition.name(), added);
	}

	private Set<String> identifiers(List<KeycloakRole> roles) {
		return roles.stream().map(KeycloakRole::id).collect(Collectors.toSet());
	}

	private void deleteObsoleteManagedRoles(String accessToken, String clientIdentifier,
		Set<String> desiredNames) {
		List<String> owner = List.of(keycloakProperties.resourceClientId());
		for (KeycloakRole existing : adminClient.listAllRoles(accessToken, clientIdentifier)) {
			if (!desiredNames.contains(existing.name())
				&& owner.equals(existing.attributes().get(OWNER_ATTRIBUTE))) {
				adminClient.deleteRole(accessToken, clientIdentifier, existing.name());
				log.info("Deleted obsolete managed client role {}", existing.name());
			}
		}
	}
}
