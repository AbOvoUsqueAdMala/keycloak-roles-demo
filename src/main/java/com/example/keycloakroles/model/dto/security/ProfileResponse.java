package com.example.keycloakroles.model.dto.security;

import java.util.List;

public record ProfileResponse(String subject, List<String> authorities) {
}
