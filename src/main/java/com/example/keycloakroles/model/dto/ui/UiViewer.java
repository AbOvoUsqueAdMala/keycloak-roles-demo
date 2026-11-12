package com.example.keycloakroles.model.dto.ui;

import java.util.List;

public record UiViewer(
	String displayName, String email, String subject, String initials,
	List<String> permissions,
	boolean canReadOrders, boolean canWriteOrders, boolean canReadUsers, boolean canReadAudit) {
}
