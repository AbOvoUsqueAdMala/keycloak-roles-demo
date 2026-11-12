package com.example.keycloakroles.security;

import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@RequiredArgsConstructor
public class KeycloakOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

	private final JwtDecoder jwtDecoder;
	private final KeycloakAuthoritiesConverter authoritiesConverter;
	private final OidcUserService delegate = createDelegate();

	@Override
	public OidcUser loadUser(OidcUserRequest userRequest) {
		OidcUser user = delegate.loadUser(userRequest);
		Jwt accessToken = jwtDecoder.decode(userRequest.getAccessToken().getTokenValue());
		if (!user.getSubject().equals(accessToken.getSubject())) {
			throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"),
				"ID token and access token subjects do not match");
		}
		Set<GrantedAuthority> authorities = new LinkedHashSet<>(user.getAuthorities());
		authorities.addAll(authoritiesConverter.convert(accessToken));
		return new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo());
	}

	private static OidcUserService createDelegate() {
		OidcUserService userService = new OidcUserService();
		// The verified ID token already contains profile/email; no UserInfo request is needed.
		userService.setRetrieveUserInfo(userRequest -> false);
		return userService;
	}
}
