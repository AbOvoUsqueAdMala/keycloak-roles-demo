package com.example.keycloakroles.service.ui;

import com.example.keycloakroles.model.dto.ui.UiNavigationItem;
import com.example.keycloakroles.model.dto.ui.UiViewer;
import com.example.keycloakroles.security.ApplicationPermission;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;

@Service
public class UiViewModelService {

	public void populate(Model model, Authentication authentication) {
		UiViewer viewer = viewer(authentication);
		model.addAttribute("viewer", viewer);
		model.addAttribute("navigation", navigation(viewer));
	}

	private UiViewer viewer(Authentication authentication) {
		List<String> authorities = authentication.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority).sorted().toList();
		OidcUser user = authentication.getPrincipal() instanceof OidcUser oidcUser ? oidcUser : null;
		String displayName = displayName(authentication, user);
		String email = user != null && StringUtils.hasText(user.getEmail())
			? user.getEmail() : "Не указан";
		List<String> permissions = authorities.stream().filter(ApplicationPermission::supports).toList();
		return new UiViewer(displayName, email, authentication.getName(),
			displayName.substring(0, 1).toUpperCase(), permissions,
			authorities.contains("order:read"), authorities.contains("order:write"),
			authorities.contains("user:read"), authorities.contains("audit:read"));
	}

	private String displayName(Authentication authentication, OidcUser user) {
		if (user != null && StringUtils.hasText(user.getFullName())) {
			return user.getFullName();
		}
		if (user != null && StringUtils.hasText(user.getPreferredUsername())) {
			return user.getPreferredUsername();
		}
		return authentication.getName();
	}

	private List<UiNavigationItem> navigation(UiViewer viewer) {
		return List.of(
			new UiNavigationItem("overview", "Обзор", "Все разделы вашего пространства",
				"/", "overview", true),
			new UiNavigationItem("orders", "Заказы", "Просмотр и создание заказов",
				"/ui/orders", "orders", viewer.canReadOrders()),
			new UiNavigationItem("users", "Пользователи", "Демонстрационная команда",
				"/ui/users", "users", viewer.canReadUsers()),
			new UiNavigationItem("audit", "Аудит", "Проверка доступа к аудиту",
				"/ui/audit", "audit", viewer.canReadAudit()),
			new UiNavigationItem("profile", "Мой профиль", "Учётная запись и ваши разрешения",
				"/ui/profile", "profile", true));
	}
}
