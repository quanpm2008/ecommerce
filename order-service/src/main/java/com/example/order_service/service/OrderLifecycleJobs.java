package com.example.order_service.service;

import com.example.order_service.entity.Order.Status;
import com.example.order_service.repository.CheckoutOrderRepository;
import com.example.order_service.repository.InventoryStockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Component
@EnableScheduling
@ConditionalOnProperty("order.lifecycle.sweep-delay-ms")
public class OrderLifecycleJobs {
	private static final Logger log = LoggerFactory.getLogger(OrderLifecycleJobs.class);
	private final CheckoutOrderRepository orders;
	private final InventoryStockRepository clock;
	private final OrderLifecycleService lifecycle;

	OrderLifecycleJobs(CheckoutOrderRepository orders, InventoryStockRepository clock, OrderLifecycleService lifecycle) {
		this.orders = orders;
		this.clock = clock;
		this.lifecycle = lifecycle;
	}

	@Scheduled(fixedDelayString = "${order.lifecycle.sweep-delay-ms}")
	public void runDueActions() {
		var now = clock.databaseNow();
		var batch = PageRequest.ofSize(100);
		apply(orders.customerOverdue(Status.DELIVERED, now, batch), lifecycle::autoConfirm);
		apply(orders.sellerOverdue(List.of(Status.PAID, Status.PACKING), now, batch), lifecycle::expireSellerAction);
	}

	private void apply(List<UUID> ids, Consumer<UUID> action) {
		for (UUID id : ids) {
			try {
				action.accept(id);
			} catch (RuntimeException failure) {
				log.warn("Order lifecycle job could not process {}", id, failure);
			}
		}
	}
}
