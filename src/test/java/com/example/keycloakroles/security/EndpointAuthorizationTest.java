package com.example.keycloakroles.security;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.keycloakroles.service.orders.OrderService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"app.role-sync.enabled=false", "app.ui.client-secret=test-secret"})
@AutoConfigureMockMvc
class EndpointAuthorizationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private OrderService orderService;
	@MockitoBean
	private JwtDecoder jwtDecoder;

	@Test
	void publicEndpointWorksWithoutTokenAndProtectedEndpointReturns401() throws Exception {
		mockMvc.perform(get("/api/public")).andExpect(status().isOk());
		mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
	}

	@Test
	void readerCanReadButCannotCreateOrdersOrAccessAdministration() throws Exception {
		authenticateAs("READER", "order:read");

		mockMvc.perform(get("/api/orders").header("Authorization", "Bearer test-token"))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/orders").header("Authorization", "Bearer test-token")
			.contentType(MediaType.APPLICATION_JSON).content("{\"item\":\"Book\"}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/users").header("Authorization", "Bearer test-token"))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/admin/audit").header("Authorization", "Bearer test-token"))
			.andExpect(status().isForbidden());
	}

	@Test
	void managerCanCreateOrdersButCannotUseAdminEndpoint() throws Exception {
		authenticateAs("MANAGER", "order:read", "order:write");

		mockMvc.perform(post("/api/orders").header("Authorization", "Bearer test-token")
			.contentType(MediaType.APPLICATION_JSON).content("{\"item\":\"Book\"}"))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.item").value("Book"));
		mockMvc.perform(get("/api/admin/audit").header("Authorization", "Bearer test-token"))
			.andExpect(status().isForbidden());
	}

	@Test
	void administratorCanReadUsersAndAuditEndpoint() throws Exception {
		authenticateAs("ADMIN", "order:read", "order:write", "user:read");

		mockMvc.perform(get("/api/users").header("Authorization", "Bearer test-token"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/admin/audit").header("Authorization", "Bearer test-token"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/profile").header("Authorization", "Bearer test-token"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.subject").value("user-1"));
	}

	@Test
	void rolesFromAnotherClientDoNotAuthorizeThisApi() throws Exception {
		Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "RS256").subject("user-1")
			.claim("resource_access", Map.of("another-api", Map.of("roles", List.of("order:read"))))
			.build();
		when(jwtDecoder.decode(anyString())).thenReturn(jwt);

		mockMvc.perform(get("/api/orders").header("Authorization", "Bearer test-token"))
			.andExpect(status().isForbidden());
	}

	@Test
	void malformedOrderIsRejectedForAnAuthorizedManager() throws Exception {
		authenticateAs("MANAGER", "order:write");

		mockMvc.perform(post("/api/orders").header("Authorization", "Bearer test-token")
			.contentType(MediaType.APPLICATION_JSON).content("{\"item\":\" \"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void directServiceInvocationAlsoRequiresAuthorization() {
		org.assertj.core.api.Assertions.assertThatThrownBy(orderService::findOrders)
			.isInstanceOf(org.springframework.security.core.AuthenticationException.class);
	}

	private void authenticateAs(String... authorities) {
		Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "RS256").subject("user-1")
			.claim("resource_access", Map.of("orders-api", Map.of("roles", List.of(authorities))))
			.build();
		when(jwtDecoder.decode(anyString())).thenReturn(jwt);
	}
}
