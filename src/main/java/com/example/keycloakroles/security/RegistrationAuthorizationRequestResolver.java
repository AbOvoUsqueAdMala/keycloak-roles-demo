package com.example.keycloakroles.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

public class RegistrationAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

	private final DefaultOAuth2AuthorizationRequestResolver delegate;

	public RegistrationAuthorizationRequestResolver(ClientRegistrationRepository registrations) {
		delegate = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
		delegate.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
	}

	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
		return customize(delegate.resolve(request), request);
	}

	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
		return customize(delegate.resolve(request, registrationId), request);
	}

	private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest authorizationRequest,
		HttpServletRequest request) {
		if (authorizationRequest == null || !"register".equals(request.getParameter("screen"))) {
			return authorizationRequest;
		}
		return OAuth2AuthorizationRequest.from(authorizationRequest)
			.additionalParameters(parameters -> parameters.put("prompt", "create")).build();
	}
}
