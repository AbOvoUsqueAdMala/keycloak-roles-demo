package com.example.keycloakroles.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.keycloakroles.config.KeycloakProperties;
import com.example.keycloakroles.config.RoleSyncProperties;
import com.example.keycloakroles.model.dto.keycloak.KeycloakRole;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

class KeycloakAdminClientTest {

	private static final String BASE_URL = "http://keycloak.test";
	private static final String CLIENT_URL = BASE_URL + "/admin/realms/demo/clients/client-uuid";
	private MockRestServiceServer server;
	private KeycloakAdminClient adminClient;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		URI base = URI.create(BASE_URL);
		adminClient = new KeycloakAdminClient(builder.build(),
			new KeycloakProperties(base, base, base, "demo", "orders-api"),
			new RoleSyncProperties(true, false, "roles-sync", "test-secret"));
	}

	@Test
	void obtainsTokenWithClientCredentialsAndResolvesClientUuid() {
		server.expect(requestTo(BASE_URL + "/realms/demo/protocol/openid-connect/token"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().string(
				"grant_type=client_credentials&client_id=roles-sync&client_secret=test-secret"))
			.andRespond(withSuccess("{\"access_token\":\"service-token\",\"expires_in\":300}",
				MediaType.APPLICATION_JSON));
		server.expect(requestTo(BASE_URL + "/admin/realms/demo/clients?clientId=orders-api"))
			.andExpect(header("Authorization", "Bearer service-token"))
			.andRespond(withSuccess("[{\"id\":\"client-uuid\",\"clientId\":\"orders-api\",\"enabled\":true}]",
				MediaType.APPLICATION_JSON));

		String accessToken = adminClient.obtainAccessToken();

		assertThat(adminClient.findResourceClientIdentifier(accessToken)).isEqualTo("client-uuid");
		server.verify();
	}

	@Test
	void distinguishesMissingRoleFromForbiddenAdministration() {
		server.expect(requestTo(CLIENT_URL + "/roles/READER"))
			.andRespond(withStatus(HttpStatus.NOT_FOUND));
		server.expect(requestTo(CLIENT_URL + "/roles/ADMIN"))
			.andRespond(withStatus(HttpStatus.FORBIDDEN));

		assertThat(adminClient.findRole("token", "client-uuid", "READER")).isEmpty();
		assertThatThrownBy(() -> adminClient.findRole("token", "client-uuid", "ADMIN"))
			.isInstanceOf(HttpClientErrorException.Forbidden.class);
		server.verify();
	}

	@Test
	void sendsCompositeRemovalAsDeleteWithJsonBody() {
		server.expect(requestTo(CLIENT_URL + "/roles/MANAGER/composites"))
			.andExpect(method(HttpMethod.DELETE))
			.andExpect(header("Authorization", "Bearer token"))
			.andExpect(content().json("[{\"id\":\"obsolete-id\",\"name\":\"obsolete\","
				+ "\"description\":\"\",\"attributes\":{}}]"))
			.andRespond(withStatus(HttpStatus.NO_CONTENT));

		adminClient.removeComposites("token", "client-uuid", "MANAGER",
			List.of(new KeycloakRole("obsolete-id", "obsolete", "", Map.of())));

		server.verify();
	}

	@Test
	void listsAllPagesWithAttributesBeforePruning() {
		String role = "{\"id\":\"role-id\",\"name\":\"CUSTOM\",\"attributes\":{\"managed-by\":[\"orders-api\"]}}";
		String firstPage = "[" + String.join(",", Collections.nCopies(100, role)) + "]";
		server.expect(requestTo(CLIENT_URL + "/roles?first=0&max=100&briefRepresentation=false"))
			.andRespond(withSuccess(firstPage, MediaType.APPLICATION_JSON));
		server.expect(requestTo(CLIENT_URL + "/roles?first=100&max=100&briefRepresentation=false"))
			.andRespond(withSuccess("[" + role + "]", MediaType.APPLICATION_JSON));

		List<KeycloakRole> roles = adminClient.listAllRoles("token", "client-uuid");

		assertThat(roles).hasSize(101);
		assertThat(roles.getLast().attributes()).containsEntry("managed-by", List.of("orders-api"));
		server.verify();
	}
}
