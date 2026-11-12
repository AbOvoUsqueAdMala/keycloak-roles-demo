package com.example.keycloakroles.repository.orders;

import com.example.keycloakroles.model.dto.orders.OrderResponse;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Bounded, non-persistent demo storage. Replace with JPA when persistence is needed. */
@Repository
public class DemoOrderRepository {

	private static final int MAX_ORDERS = 100;
	private final ArrayDeque<OrderResponse> orders = new ArrayDeque<>(List.of(
		new OrderResponse(UUID.fromString("00000000-0000-0000-0000-000000000001"), "Demo order")));

	public synchronized List<OrderResponse> findAll() {
		return List.copyOf(orders);
	}

	public synchronized OrderResponse create(String item) {
		OrderResponse order = new OrderResponse(UUID.randomUUID(), item);
		if (orders.size() == MAX_ORDERS) {
			orders.removeFirst();
		}
		orders.addLast(order);
		return order;
	}
}
