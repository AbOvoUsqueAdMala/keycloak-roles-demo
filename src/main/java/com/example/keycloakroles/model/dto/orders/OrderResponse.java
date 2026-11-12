package com.example.keycloakroles.model.dto.orders;

import java.util.UUID;

public record OrderResponse(UUID identifier, String item) {
}
