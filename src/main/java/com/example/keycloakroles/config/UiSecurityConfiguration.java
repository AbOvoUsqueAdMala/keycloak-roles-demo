package com.example.keycloakroles.config;

import com.example.keycloakroles.security.KeycloakAuthoritiesConverter;
import com.example.keycloakroles.security.KeycloakOidcUserService;
import com.example.keycloakroles.security.RegistrationAuthorizationRequestResolver;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class UiSecurityConfiguration {

	@Bean
	ClientRegistrationRepository clientRegistrationRepository(KeycloakProperties keycloak,
		UiProperties uiProperties) {
		if (!StringUtils.hasText(uiProperties.clientSecret())) {
			throw new IllegalStateException("KEYCLOAK_UI_CLIENT_SECRET is required for browser login");
		}
		String issuer = keycloak.issuerUri().toString();
		String internalRealm = keycloak.internalUrl() + "/realms/" + keycloak.realm();
		ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
			.clientName("Keycloak").clientId(uiProperties.clientId())
			.clientSecret(uiProperties.clientSecret())
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
			.scope("openid", "profile", "email")
			.authorizationUri(issuer + "/protocol/openid-connect/auth")
			.tokenUri(internalRealm + "/protocol/openid-connect/token")
			.jwkSetUri(keycloak.jwkSetUri().toString())
			.userInfoUri(internalRealm + "/protocol/openid-connect/userinfo")
			.userNameAttributeName(IdTokenClaimNames.SUB).issuerUri(issuer)
			.providerConfigurationMetadata(Map.of(
				"end_session_endpoint", issuer + "/protocol/openid-connect/logout"))
			.build();
		return new InMemoryClientRegistrationRepository(registration);
	}

	@Bean
	KeycloakOidcUserService keycloakOidcUserService(JwtDecoder jwtDecoder,
		KeycloakProperties keycloakProperties) {
		return new KeycloakOidcUserService(jwtDecoder,
			new KeycloakAuthoritiesConverter(keycloakProperties.resourceClientId()));
	}

	@Bean
	@Order(2)
	SecurityFilterChain uiSecurityFilterChain(HttpSecurity http,
		ClientRegistrationRepository registrations, KeycloakOidcUserService userService) throws Exception {
		var resolver = new RegistrationAuthorizationRequestResolver(registrations);
		var logoutHandler = new OidcClientInitiatedLogoutSuccessHandler(registrations);
		logoutHandler.setPostLogoutRedirectUri("{baseUrl}/");
		return http
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/assets/**", "/oauth2/**", "/login/**", "/error").permitAll()
				.anyRequest().authenticated())
			.oauth2Login(login -> login
				.authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
				.userInfoEndpoint(endpoint -> endpoint.oidcUserService(userService))
				.defaultSuccessUrl("/", true))
			.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
				new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/keycloak")))
			.logout(logout -> logout.logoutSuccessHandler(logoutHandler))
			.build();
	}
}
