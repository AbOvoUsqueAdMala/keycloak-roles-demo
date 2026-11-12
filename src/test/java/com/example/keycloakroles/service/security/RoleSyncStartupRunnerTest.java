package com.example.keycloakroles.service.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class RoleSyncStartupRunnerTest {

	@Test
	void startupFailsWhenRoleSynchronizationFails() {
		KeycloakRoleSynchronizer synchronizer = mock(KeycloakRoleSynchronizer.class);
		doThrow(new IllegalStateException("Forbidden")).when(synchronizer).synchronize();
		RoleSyncStartupRunner runner = new RoleSyncStartupRunner(synchronizer);

		assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
			.isInstanceOf(IllegalStateException.class).hasMessageContaining("startup aborted")
			.hasRootCauseMessage("Forbidden");
	}
}
