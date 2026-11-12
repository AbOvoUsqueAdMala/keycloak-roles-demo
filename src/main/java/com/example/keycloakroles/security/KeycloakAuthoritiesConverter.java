package com.example.keycloakroles.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

@RequiredArgsConstructor
public class KeycloakAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

	private final String resourceClientId;

	@Override
	public Collection<GrantedAuthority> convert(Jwt jwt) {
		Object resourceAccess = jwt.getClaims().get("resource_access");
		if (!(resourceAccess instanceof Map<?, ?> clients)
			|| !(clients.get(resourceClientId) instanceof Map<?, ?> client)
			|| !(client.get("roles") instanceof Collection<?> roleNames)) {
			return Set.of();
		}
		Set<GrantedAuthority> authorities = new LinkedHashSet<>();
		for (Object roleName : roleNames) {
			if (roleName instanceof String name && !name.isBlank()) {
				String authority = name.contains(":") ? name : "ROLE_" + name;
				authorities.add(new SimpleGrantedAuthority(authority));
			}
		}
		return authorities;
	}
}
