package com.example.keycloakroles.service.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.keycloakroles.model.dto.orders.CreateOrderRequest;
import com.example.keycloakroles.repository.orders.DemoOrderRepository;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class OrderServiceAuthorizationTest {

	private AnnotationConfigApplicationContext context;
	private OrderService orderService;

	@BeforeEach
	void setUp() {
		context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		orderService = context.getBean(OrderService.class);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
		context.close();
	}

	@Test
	void readerCannotWriteThroughTheServiceBoundary() {
		authenticate("order:read");

		assertThat(orderService.findOrders()).hasSize(1);
		assertThatThrownBy(() -> orderService.createOrder(new CreateOrderRequest("Book")))
			.isInstanceOf(AccessDeniedException.class);
		assertThat(orderService.findOrders()).hasSize(1);
	}

	@Test
	void managerWithWritePermissionCanCreateAnOrder() {
		authenticate("order:read", "order:write");

		assertThat(orderService.createOrder(new CreateOrderRequest("Book")).item()).isEqualTo("Book");
		assertThat(orderService.findOrders()).hasSize(2);
	}

	@Test
	void adminRoleAloneDoesNotBypassPermissionChecks() {
		authenticate("ROLE_ADMIN");

		assertThatThrownBy(orderService::findOrders).isInstanceOf(AccessDeniedException.class);
	}

	private void authenticate(String... authorities) {
		var grantedAuthorities = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
		var authentication = UsernamePasswordAuthenticationToken.authenticated(
			"user-1", "unused", grantedAuthorities);
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	@Configuration(proxyBeanMethods = false)
	@EnableMethodSecurity
	static class TestConfiguration {

		@Bean
		OrderService orderService() {
			return new OrderService(new DemoOrderRepository());
		}
	}
}
