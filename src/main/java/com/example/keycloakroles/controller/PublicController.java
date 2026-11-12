package com.example.keycloakroles.controller;

import com.example.keycloakroles.model.dto.MessageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicController {

	@GetMapping("/api/public")
	public MessageResponse publicMessage() {
		return new MessageResponse("Public endpoint: no token required");
	}
}
