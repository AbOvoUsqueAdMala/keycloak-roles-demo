package com.example.keycloakroles.controller.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UiLoginController {

	@GetMapping("/login")
	public String loginError() {
		return "login-error";
	}
}
