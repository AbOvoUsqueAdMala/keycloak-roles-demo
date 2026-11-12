package com.example.keycloakroles.service.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.role-sync.enabled", havingValue = "true")
public class RoleSyncStartupRunner implements ApplicationRunner {

	private final KeycloakRoleSynchronizer roleSynchronizer;

	@Override
	public void run(ApplicationArguments arguments) {
		try {
			roleSynchronizer.synchronize();
		} catch (RuntimeException exception) {
			throw new IllegalStateException("Keycloak role synchronization failed; startup aborted",
				exception);
		}
	}
}
