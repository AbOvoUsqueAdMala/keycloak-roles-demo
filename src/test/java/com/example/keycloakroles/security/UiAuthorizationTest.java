package com.example.keycloakroles.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest(properties = {"app.ui.client-secret=test-secret"})
@AutoConfigureMockMvc
class UiAuthorizationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void anonymousBrowserStartsSignInFlow() throws Exception {
		mockMvc.perform(get("/")).andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/oauth2/authorization/keycloak"));
	}

	@Test
	void registrationRequestPreservesOauthStateNonceAndPkce() throws Exception {
		var result = mockMvc.perform(get("/oauth2/authorization/keycloak").param("screen", "register"))
			.andExpect(status().is3xxRedirection()).andReturn();
		var parameters = UriComponentsBuilder.fromUriString(
			result.getResponse().getRedirectedUrl()).build().getQueryParams();

		assertThat(parameters.getFirst("prompt")).isEqualTo("create");
		assertThat(parameters.getFirst("client_id")).isEqualTo("orders-ui");
		assertThat(parameters.getFirst("state")).isNotBlank();
		assertThat(parameters.getFirst("nonce")).isNotBlank();
		assertThat(parameters.getFirst("code_challenge")).isNotBlank();
		assertThat(parameters.getFirst("code_challenge_method")).isEqualTo("S256");
	}

	@Test
	void existingUsersCanChooseTheNormalLoginFlow() throws Exception {
		var result = mockMvc.perform(get("/oauth2/authorization/keycloak"))
			.andExpect(status().is3xxRedirection()).andReturn();

		assertThat(UriComponentsBuilder.fromUriString(result.getResponse().getRedirectedUrl())
			.build().getQueryParams()).doesNotContainKey("prompt");
	}

	@Test
	void readerSeesAllSectionsAndReceivesAUsefulDeniedPage() throws Exception {
		var reader = oidcLogin().authorities(new SimpleGrantedAuthority("order:read"));
		mockMvc.perform(get("/").with(reader)).andExpect(status().isOk())
			.andExpect(content().string(containsString("Заказы")))
			.andExpect(content().string(containsString("Пользователи")))
			.andExpect(content().string(containsString("Аудит")))
			.andExpect(content().string(containsString("Мой профиль")));
		mockMvc.perform(get("/ui/orders").with(reader)).andExpect(status().isOk())
			.andExpect(content().string(containsString("disabled=\"disabled\"")));
		mockMvc.perform(get("/ui/users").with(reader)).andExpect(status().isForbidden())
			.andExpect(content().string(containsString("Этот раздел пока недоступен")));
		mockMvc.perform(get("/ui/audit").with(reader)).andExpect(status().isForbidden());
	}

	@Test
	void managerCanCreateAnOrderWithCsrfAndCannotWithoutIt() throws Exception {
		var manager = oidcLogin().authorities(new SimpleGrantedAuthority("order:read"),
			new SimpleGrantedAuthority("order:write"));
		mockMvc.perform(post("/ui/orders").with(manager).param("item", "No CSRF"))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/ui/orders").with(manager).with(csrf()).param("item", "Browser order"))
			.andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/ui/orders"));
		mockMvc.perform(get("/ui/orders").with(manager)).andExpect(status().isOk())
			.andExpect(content().string(containsString("Browser order")))
			.andExpect(content().string(containsString("name=\"_csrf\"")));
	}

	@Test
	void readerCannotCreateOrdersEvenWithValidCsrf() throws Exception {
		mockMvc.perform(post("/ui/orders").with(oidcLogin().authorities(
			new SimpleGrantedAuthority("order:read"))).with(csrf()).param("item", "Forbidden"))
			.andExpect(status().isForbidden());
	}

	@Test
	void administratorCanOpenUsersAuditAndProfile() throws Exception {
		var administrator = oidcLogin().authorities(new SimpleGrantedAuthority("audit:read"),
			new SimpleGrantedAuthority("user:read"));
		mockMvc.perform(get("/ui/users").with(administrator)).andExpect(status().isOk());
		mockMvc.perform(get("/ui/audit").with(administrator)).andExpect(status().isOk());
		mockMvc.perform(get("/ui/profile").with(administrator)).andExpect(status().isOk());
	}

	@Test
	void browserSessionDoesNotAuthenticateTheStatelessApi() throws Exception {
		var authorities = List.of(new SimpleGrantedAuthority("order:read"));
		var idToken = OidcIdToken.withTokenValue("test-id-token").subject("user-1").build();
		var principal = new DefaultOidcUser(authorities, idToken);
		var authentication = new OAuth2AuthenticationToken(principal, authorities, "keycloak");
		var context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

		mockMvc.perform(get("/api/orders").session(session)).andExpect(status().isUnauthorized());
	}

	@Test
	void staticAssetsArePublicAndLogoutRequiresCsrf() throws Exception {
		mockMvc.perform(get("/assets/workspace.css")).andExpect(status().isOk());
		mockMvc.perform(get("/login").param("error", "")).andExpect(status().isOk())
			.andExpect(content().string(containsString("Повторить вход")));
		mockMvc.perform(post("/logout").with(oidcLogin())).andExpect(status().isForbidden());
	}
}
