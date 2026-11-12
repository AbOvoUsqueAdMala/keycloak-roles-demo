package com.example.keycloakroles.controller.security;

import com.example.keycloakroles.model.dto.MessageResponse;
import com.example.keycloakroles.model.dto.security.ProfileResponse;
import com.example.keycloakroles.security.ApplicationPermission;
import com.example.keycloakroles.service.security.DemoAdministrationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AccessController {

	private final DemoAdministrationService administrationService;

	@GetMapping("/api/profile")
	public ProfileResponse profile(Authentication authentication) {
		List<String> authorities = authentication.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority).filter(ApplicationPermission::supports).sorted().toList();
		return new ProfileResponse(authentication.getName(), authorities);
	}

	@GetMapping("/api/users")
	public List<String> users() {
		return administrationService.findDemoUsers();
	}

	@GetMapping("/api/admin/audit")
	public MessageResponse audit() {
		return administrationService.readAuditExample();
	}
}
