package com.example.keycloakroles.model.dto.ui;

public record UiNavigationItem(
	String section, String title, String description, String path, String icon, boolean accessible) {
}
