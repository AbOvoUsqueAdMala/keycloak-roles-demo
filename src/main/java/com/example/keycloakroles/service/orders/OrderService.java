package com.example.keycloakroles.service.orders;

import com.example.keycloakroles.model.dto.orders.CreateOrderRequest;
import com.example.keycloakroles.model.dto.orders.OrderResponse;
import com.example.keycloakroles.repository.orders.DemoOrderRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderService {

	private final DemoOrderRepository orderRepository;

	@PreAuthorize("hasAuthority('order:read')")
	public List<OrderResponse> findOrders() {
		return orderRepository.findAll();
	}

	@PreAuthorize("hasAuthority('order:write')")
	public OrderResponse createOrder(CreateOrderRequest request) {
		return orderRepository.create(request.item());
	}
}
