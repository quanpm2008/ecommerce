package com.example.order_service.repository;

import com.example.order_service.client.PayIdentityClient.ShippingAddress;
import com.example.order_service.dto.response.CheckoutResponse;
import com.example.order_service.entity.CheckoutCommand;
import com.example.order_service.entity.CheckoutIdempotency;
import com.example.order_service.entity.CheckoutSession;
import com.example.order_service.entity.Order;
import com.example.order_service.entity.OrderItem;
import com.example.order_service.entity.Reservation;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class CheckoutCommitRepository {
	public static final String PAY_ENDPOINT = "/api/v1/orders/checkout/pay";
	private final EntityManager entities;
	private final InventoryStockRepository inventory;

	public CheckoutCommitRepository(EntityManager entities, InventoryStockRepository inventory) {
		this.entities = entities;
		this.inventory = inventory;
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public CheckoutResponse commit(NewCheckout checkout) {
		UUID id = UUID.randomUUID();
		long total = checkout.lines().stream().mapToLong(Line::total).reduce(0, Math::addExact);
		Instant created = inventory.databaseNow();
		Times times = new Times(created, created.plus(Duration.ofMinutes(15)));
		entities.persist(new CheckoutSession(id, checkout.intent(), checkout.customer(), CheckoutSession.Source.BUY_NOW,
				checkout.key(), checkout.hash(), total, CheckoutSession.Status.AWAITING_PAYMENT,
				checkout.addressId(), times.created(), times.expires()));
		entities.flush();
		for (var line : checkout.lines().stream().sorted(Comparator.comparing(Line::product)).toList()) {
			reserve(line);
		}
		var orders = new ArrayList<CheckoutResponse.OrderSummary>();
		for (var entry : checkout.lines().stream().collect(Collectors.groupingBy(Line::seller)).entrySet()) {
			orders.add(writeOrder(id, times, checkout, entry.getKey(), entry.getValue()));
		}
		entities.persist(new CheckoutIdempotency(
				new CheckoutIdempotency.Key(checkout.customer(), "CUSTOMER", PAY_ENDPOINT, checkout.key()), checkout.hash(), id));
		for (String command : List.of("CreateProviderSession", "ExpireCheckout")) {
			entities.persist(new CheckoutCommand(UUID.randomUUID(), id, command, times.expires(), times.created()));
		}
		entities.flush();
		return new CheckoutResponse(id, "BUY_NOW", "AWAITING_PAYMENT", null, "NONE", null, total, 0,
				times.expires(), null, checkout.address(), List.copyOf(orders), List.of(), true);
	}

	private void reserve(Line line) {
		int changed = inventory.reserve(line.product(), line.seller(), line.quantity());
		if (changed != 1) {
			throw new StockUnavailable();
		}
	}

	private CheckoutResponse.OrderSummary writeOrder(UUID checkoutId, Times times, NewCheckout checkout, UUID seller,
			List<Line> lines) {
		UUID order = UUID.randomUUID();
		Line first = lines.getFirst();
		long total = lines.stream().mapToLong(Line::total).reduce(0, Math::addExact);
		ShippingAddress a = checkout.address();
		entities.persist(new Order(order, checkoutId, seller, first.sellerName(), checkout.customer(), total,
				checkout.commissionBps(), a.recipientName(), a.recipientPhone(), a.provinceCode(), a.provinceName(),
				a.wardCode(), a.wardName(), a.addressDetail(), Order.Status.AWAITING_PAYMENT, times.created(), times.created()));
		entities.flush();
		var reservations = new ArrayList<Reservation>();
		for (Line line : lines) {
			UUID item = UUID.randomUUID();
			entities.persist(new OrderItem(item, order, line.product(), line.productName(), line.coverImageUrl(), line.price(),
					line.quantity()));
			reservations.add(new Reservation(UUID.randomUUID(), item, line.product(), line.quantity(),
					Reservation.Status.HELD, times.expires(), times.created()));
		}
		entities.flush();
		reservations.forEach(entities::persist);
		entities.flush();
		return new CheckoutResponse.OrderSummary(order, checkoutId, new CheckoutResponse.Seller(seller, first.sellerName()),
				"AWAITING_PAYMENT", total, lines.size(), new CheckoutResponse.FirstItem(first.productName(), first.coverImageUrl()),
				times.created(), null, null);
	}

	private record Times(Instant created, Instant expires) {
	}

	public record NewCheckout(UUID customer, UUID intent, String key, String hash, UUID addressId,
			ShippingAddress address, int commissionBps, List<Line> lines) {
	}

	public record Line(UUID product, UUID seller, String sellerName, String productName, String coverImageUrl,
			long price, int quantity) {
		long total() {
			return Math.multiplyExact(price, quantity);
		}
	}

	public static final class StockUnavailable extends RuntimeException {
	}
}
