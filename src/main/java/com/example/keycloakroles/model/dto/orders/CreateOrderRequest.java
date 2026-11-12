package com.example.keycloakroles.model.dto.orders;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(@NotBlank @Size(max = 200) String item) {
}
