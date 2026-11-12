package com.example.keycloakroles.security;

import com.example.keycloakroles.model.dto.security.RoleDefinition;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class RoleCatalog {

	public List<RoleDefinition> definitions() {
		Stream<RoleDefinition> permissions = Arrays.stream(ApplicationPermission.values())
			.map(permission -> new RoleDefinition(
				permission.getCode(), permission.getDescription(), Set.of()));
		Stream<RoleDefinition> roles = Arrays.stream(ApplicationRole.values())
			.map(role -> new RoleDefinition(role.name(), role.getDescription(),
				role.getPermissions().stream()
					.map(ApplicationPermission::getCode).collect(Collectors.toSet())));
		return Stream.concat(permissions, roles).toList();
	}
}
