package com.example.keycloakroles.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.keycloakroles.config.KeycloakProperties;
import com.example.keycloakroles.config.RoleSyncProperties;
import com.example.keycloakroles.model.dto.keycloak.KeycloakRole;
import com.example.keycloakroles.model.dto.security.RoleDefinition;
import com.example.keycloakroles.security.RoleCatalog;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

@ExtendWith(MockitoExtension.class)
class KeycloakRoleSynchronizerTest {

	private static final String ACCESS_TOKEN = "service-token";
	private static final String CLIENT_IDENTIFIER = "orders-client-uuid";
	private static final Map<String, List<String>> OWNED = Map.of("managed-by", List.of("orders-api"));
	private static final RoleDefinition READ = new RoleDefinition("order:read", "Read orders", Set.of());
	private static final RoleDefinition MANAGER = new RoleDefinition(
		"MANAGER", "Manage orders", Set.of("order:read"));
	@Mock
	private KeycloakAdminClient adminClient;
	@Mock
	private RoleCatalog roleCatalog;
	private KeycloakRoleSynchronizer synchronizer;

	@BeforeEach
	void setUp() {
		synchronizer = synchronizer(false);
		when(adminClient.obtainAccessToken()).thenReturn(ACCESS_TOKEN);
		when(adminClient.findResourceClientIdentifier(ACCESS_TOKEN)).thenReturn(CLIENT_IDENTIFIER);
	}

	@Test
	void createsMissingRoleWithOwnershipMarker() {
		when(roleCatalog.definitions()).thenReturn(List.of(READ));
		when(adminClient.findRole(ACCESS_TOKEN, CLIENT_IDENTIFIER, READ.name()))
			.thenReturn(Optional.empty(), Optional.of(role(READ)));
		when(adminClient.listComposites(ACCESS_TOKEN, CLIENT_IDENTIFIER, READ.name())).thenReturn(List.of());

		synchronizer.synchronize();

		ArgumentCaptor<KeycloakRole> created = ArgumentCaptor.forClass(KeycloakRole.class);
		verify(adminClient).createRole(eq(ACCESS_TOKEN), eq(CLIENT_IDENTIFIER), created.capture());
		assertThat(created.getValue().id()).isNull();
		assertThat(created.getValue().attributes()).isEqualTo(OWNED);
	}

	@Test
	void updatesDescriptionAndPreservesIdentifierAndOtherAttributes() {
		when(roleCatalog.definitions()).thenReturn(List.of(READ));
		KeycloakRole existing = new KeycloakRole("stable-id", READ.name(), "Old description",
			Map.of("custom", List.of("keep")));
		when(adminClient.findRole(ACCESS_TOKEN, CLIENT_IDENTIFIER, READ.name()))
			.thenReturn(Optional.of(existing));
		when(adminClient.listComposites(ACCESS_TOKEN, CLIENT_IDENTIFIER, READ.name())).thenReturn(List.of());

		synchronizer.synchronize();

		ArgumentCaptor<KeycloakRole> updated = ArgumentCaptor.forClass(KeycloakRole.class);
		verify(adminClient).updateRole(eq(ACCESS_TOKEN), eq(CLIENT_IDENTIFIER), updated.capture());
		assertThat(updated.getValue().id()).isEqualTo("stable-id");
		assertThat(updated.getValue().description()).isEqualTo(READ.description());
		assertThat(updated.getValue().attributes()).containsEntry("custom", List.of("keep"))
			.containsEntry("managed-by", List.of("orders-api"));
	}

	@Test
	void removesStaleGrantsAndAddsMissingPermissions() {
		stubExisting(READ, List.of());
		KeycloakRole unwanted = new KeycloakRole("unexpected-id", "unwanted", "", Map.of());
		stubExisting(MANAGER, List.of(unwanted));
		when(roleCatalog.definitions()).thenReturn(List.of(READ, MANAGER));

		synchronizer.synchronize();

		verify(adminClient).removeComposites(ACCESS_TOKEN, CLIENT_IDENTIFIER,
			MANAGER.name(), List.of(unwanted));
		verify(adminClient).addComposites(ACCESS_TOKEN, CLIENT_IDENTIFIER,
			MANAGER.name(), List.of(role(READ)));
	}

	@Test
	void repeatedSynchronizationDoesNotRewriteUnchangedRoles() {
		stubExisting(READ, List.of());
		stubExisting(MANAGER, List.of(role(READ)));
		when(roleCatalog.definitions()).thenReturn(List.of(READ, MANAGER));

		synchronizer.synchronize();
		synchronizer.synchronize();

		verify(adminClient, never()).createRole(any(), any(), any());
		verify(adminClient, never()).updateRole(any(), any(), any());
		verify(adminClient, never()).deleteRole(any(), any(), any());
		verify(adminClient, never()).addComposites(any(), any(), any(),
			org.mockito.ArgumentMatchers.argThat(members -> !members.isEmpty()));
		verify(adminClient, never()).removeComposites(any(), any(), any(),
			org.mockito.ArgumentMatchers.argThat(members -> !members.isEmpty()));
	}

	@Test
	void concurrentCreationConflictIsFollowedByReadingTheExistingRole() {
		when(roleCatalog.definitions()).thenReturn(List.of(READ));
		when(adminClient.findRole(ACCESS_TOKEN, CLIENT_IDENTIFIER, READ.name()))
			.thenReturn(Optional.empty(), Optional.of(role(READ)));
		org.mockito.Mockito.doThrow(HttpClientErrorException.create(
			HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY, new byte[0], null))
			.when(adminClient).createRole(any(), any(), any());
		when(adminClient.listComposites(ACCESS_TOKEN, CLIENT_IDENTIFIER, READ.name())).thenReturn(List.of());

		synchronizer.synchronize();

		verify(adminClient, never()).updateRole(any(), any(), any());
	}

	@Test
	void pruneDeletesOnlyObsoleteRolesOwnedByThisApplication() {
		synchronizer = synchronizer(true);
		stubExisting(READ, List.of());
		when(roleCatalog.definitions()).thenReturn(List.of(READ));
		KeycloakRole obsolete = new KeycloakRole("old-id", "OLD_ROLE", "", OWNED);
		KeycloakRole manual = new KeycloakRole("manual-id", "MANUAL_ROLE", "", Map.of());
		KeycloakRole foreign = new KeycloakRole("foreign-id", "OTHER_ROLE", "",
			Map.of("managed-by", List.of("another-service")));
		when(adminClient.listAllRoles(ACCESS_TOKEN, CLIENT_IDENTIFIER))
			.thenReturn(List.of(role(READ), obsolete, manual, foreign));

		synchronizer.synchronize();

		verify(adminClient).deleteRole(ACCESS_TOKEN, CLIENT_IDENTIFIER, "OLD_ROLE");
		verify(adminClient, never()).deleteRole(ACCESS_TOKEN, CLIENT_IDENTIFIER, READ.name());
		verify(adminClient, never()).deleteRole(ACCESS_TOKEN, CLIENT_IDENTIFIER, "MANUAL_ROLE");
		verify(adminClient, never()).deleteRole(ACCESS_TOKEN, CLIENT_IDENTIFIER, "OTHER_ROLE");
	}

	@Test
	void pruneIsDisabledByDefault() {
		stubExisting(READ, List.of());
		when(roleCatalog.definitions()).thenReturn(List.of(READ));

		synchronizer.synchronize();

		verify(adminClient, never()).listAllRoles(any(), any());
		verify(adminClient, never()).deleteRole(any(), any(), any());
	}

	@Test
	void refusesToTakeOverRoleManagedByAnotherApplication() {
		when(roleCatalog.definitions()).thenReturn(List.of(READ));
		KeycloakRole foreign = new KeycloakRole("foreign-id", READ.name(), "",
			Map.of("managed-by", List.of("another-service")));
		when(adminClient.findRole(ACCESS_TOKEN, CLIENT_IDENTIFIER, READ.name()))
			.thenReturn(Optional.of(foreign));

		assertThatThrownBy(synchronizer::synchronize)
			.isInstanceOf(IllegalStateException.class).hasMessageContaining("another manager");
		verify(adminClient, never()).updateRole(any(), any(), any());
		verify(adminClient, never()).addComposites(any(), any(), any(), anyList());
	}

	private void stubExisting(RoleDefinition definition, List<KeycloakRole> members) {
		when(adminClient.findRole(ACCESS_TOKEN, CLIENT_IDENTIFIER, definition.name()))
			.thenReturn(Optional.of(role(definition)));
		when(adminClient.listComposites(ACCESS_TOKEN, CLIENT_IDENTIFIER, definition.name()))
			.thenReturn(members);
	}

	private KeycloakRole role(RoleDefinition definition) {
		return new KeycloakRole("id-" + definition.name(), definition.name(), definition.description(), OWNED);
	}

	private KeycloakRoleSynchronizer synchronizer(boolean prune) {
		URI local = URI.create("http://localhost:8180");
		KeycloakProperties properties = new KeycloakProperties(local, local, local, "demo", "orders-api");
		RoleSyncProperties sync = new RoleSyncProperties(true, prune, "roles-sync", "test-secret");
		return new KeycloakRoleSynchronizer(adminClient, properties, sync, roleCatalog);
	}
}
