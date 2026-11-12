package com.example.keycloakroles.controller.ui;

import com.example.keycloakroles.model.dto.orders.CreateOrderRequest;
import com.example.keycloakroles.service.orders.OrderService;
import com.example.keycloakroles.service.security.DemoAdministrationService;
import com.example.keycloakroles.service.ui.UiViewModelService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class UiController {

	private final OrderService orderService;
	private final DemoAdministrationService administrationService;
	private final UiViewModelService viewModelService;

	@ModelAttribute
	public void sharedModel(Authentication authentication, Model model) {
		viewModelService.populate(model, authentication);
	}

	@GetMapping("/")
	public String overview(Model model) {
		return page(model, "overview", "Обзор");
	}

	@GetMapping("/ui/profile")
	public String profile(Model model) {
		return page(model, "profile", "Мой профиль");
	}

	@GetMapping("/ui/orders")
	public String orders(Model model) {
		model.addAttribute("orders", orderService.findOrders());
		model.addAttribute("newOrder", new CreateOrderRequest(""));
		return page(model, "orders", "Заказы");
	}

	@PostMapping("/ui/orders")
	public String createOrder(@Valid @ModelAttribute("newOrder") CreateOrderRequest createOrderRequest,
		BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes,
		HttpServletResponse response) {
		if (bindingResult.hasErrors()) {
			response.setStatus(HttpStatus.BAD_REQUEST.value());
			model.addAttribute("orders", orderService.findOrders());
			return page(model, "orders", "Заказы");
		}
		orderService.createOrder(createOrderRequest);
		redirectAttributes.addFlashAttribute("successMessage", "Заказ создан");
		return "redirect:/ui/orders";
	}

	@GetMapping("/ui/users")
	public String users(Model model) {
		model.addAttribute("users", administrationService.findDemoUsers());
		return page(model, "users", "Пользователи");
	}

	@GetMapping("/ui/audit")
	public String audit(Model model) {
		administrationService.readAuditExample();
		return page(model, "audit", "Аудит");
	}

	@ExceptionHandler(AccessDeniedException.class)
	public String accessDenied(Authentication authentication, Model model,
		HttpServletResponse response) {
		response.setStatus(HttpStatus.FORBIDDEN.value());
		viewModelService.populate(model, authentication);
		return page(model, "forbidden", "Доступ ограничен");
	}

	private String page(Model model, String section, String title) {
		model.addAttribute("section", section);
		model.addAttribute("pageTitle", title);
		return "workspace";
	}
}
