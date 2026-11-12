package com.example.keycloakroles.service.security;

import com.example.keycloakroles.model.dto.MessageResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class DemoAdministrationService {

	@PreAuthorize("hasAuthority('user:read')")
	public List<String> findDemoUsers() {
		return List.of("reader", "manager", "administrator");
	}

	@PreAuthorize("hasAuthority('audit:read')")
	public MessageResponse readAuditExample() {
		return new MessageResponse("Access to the audit demonstration granted");
	}
}
