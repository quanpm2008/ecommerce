package com.example.order_service.checkout;

import com.example.order_service.client.PayIdentityClient.ShippingAddress;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class CheckoutCommitRepository {
	static final String PAY_ENDPOINT = "/api/v1/orders/checkout/pay";
	private final JdbcTemplate jdbc;

	public CheckoutCommitRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public CheckoutResponse commit(NewCheckout checkout) {
		UUID id = UUID.randomUUID();
		long total = checkout.lines().stream().mapToLong(Line::total).reduce(0, Math::addExact);
		Times times = jdbc.queryForObject("""
				insert into orders.checkout_session (checkout_session_id, checkout_intent_id, customer_id,
				checkout_source, idempotency_key, request_hash, total_amount_vnd, currency, status, address_id, created_at, expires_at)
				values (?, ?, ?, 'BUY_NOW', ?, ?, ?, 'VND', 'AWAITING_PAYMENT', ?, statement_timestamp(),
				statement_timestamp() + interval '15 minutes') returning created_at, expires_at
				""", (row, index) -> new Times(row.getObject("created_at", OffsetDateTime.class),
				row.getObject("expires_at", OffsetDateTime.class)), id, checkout.intent(), checkout.customer(),
				checkout.key(), checkout.hash(), total, checkout.addressId());
		for (var line : checkout.lines().stream().sorted(Comparator.comparing(Line::product)).toList()) {
			reserve(line);
		}
		var orders = new ArrayList<CheckoutResponse.OrderSummary>();
		for (var entry : checkout.lines().stream().collect(Collectors.groupingBy(Line::seller)).entrySet()) {
			orders.add(writeOrder(id, times, checkout, entry.getKey(), entry.getValue()));
		}
		jdbc.update("""
				insert into orders.idempotency_record
				(actor_user_id, operation_context, endpoint, idempotency_key, request_hash, result_ref)
				values (?, 'CUSTOMER', ?, ?, ?, ?)
				""", checkout.customer(), PAY_ENDPOINT, checkout.key(), checkout.hash(), id);
		for (String command : List.of("CreateProviderSession", "ExpireCheckout")) {
			jdbc.update("""
					insert into orders.checkout_outbox (command_id, checkout_session_id, command_type, expires_at, created_at)
					values (?, ?, ?, ?, ?)
					""", UUID.randomUUID(), id, command, times.expires(), times.created());
		}
		return new CheckoutResponse(id, "BUY_NOW", "AWAITING_PAYMENT", null, "NONE", null, total, 0,
				times.expires().toInstant(), null, checkout.address(), List.copyOf(orders), List.of(), true);
	}

	private void reserve(Line line) {
		int changed = jdbc.update("""
				update orders.inventory set reserved_quantity = reserved_quantity + ?, row_version = row_version + 1,
				updated_at = statement_timestamp() where product_id = ? and seller_id = ?
				and on_hand_quantity - reserved_quantity >= ?
				""", line.quantity(), line.product(), line.seller(), line.quantity());
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
		jdbc.update("""
				insert into orders.purchase_order (order_id, checkout_session_id, seller_id, seller_name_snapshot, customer_id,
				total_amount_vnd, currency, commission_bps_snapshot, recipient_name, recipient_phone, province_code,
				province_name, ward_code, ward_name, address_detail, status, created_at, updated_at)
				values (?, ?, ?, ?, ?, ?, 'VND', ?, ?, ?, ?, ?, ?, ?, ?, 'AWAITING_PAYMENT', ?, ?)
				""", order, checkoutId, seller, first.sellerName(), checkout.customer(), total, checkout.commissionBps(),
				a.recipientName(), a.recipientPhone(), a.provinceCode(), a.provinceName(), a.wardCode(), a.wardName(),
				a.addressDetail(), times.created(), times.created());
		for (Line line : lines) {
			UUID item = UUID.randomUUID();
			jdbc.update("""
					insert into orders.order_item (order_item_id, order_id, product_id, product_name_snapshot, cover_media_snapshot,
					unit_price_vnd_snapshot, quantity) values (?, ?, ?, ?, ?, ?, ?)
					""", item, order, line.product(), line.productName(), line.coverImageUrl(), line.price(), line.quantity());
			jdbc.update("""
					insert into orders.reservation (reservation_id, order_item_id, product_id, quantity, status, expires_at, created_at)
					values (?, ?, ?, ?, 'HELD', ?, ?)
					""", UUID.randomUUID(), item, line.product(), line.quantity(), times.expires(), times.created());
		}
		Instant created = times.created().toInstant();
		return new CheckoutResponse.OrderSummary(order, checkoutId, new CheckoutResponse.Seller(seller, first.sellerName()),
				"AWAITING_PAYMENT", total, lines.size(), new CheckoutResponse.FirstItem(first.productName(), first.coverImageUrl()),
				created, null, null);
	}

	private record Times(OffsetDateTime created, OffsetDateTime expires) {
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

	static final class StockUnavailable extends RuntimeException {
	}
}
