package com.example.order_service.checkout;

import com.example.order_service.client.PayIdentityClient.ShippingAddress;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
class CheckoutReplayRepository {
	private final JdbcTemplate jdbc;

	CheckoutReplayRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional
	public CheckoutResponse findOrRegisterAlias(UUID customer, String key, UUID intent, String hash) {
		UUID keySession = findKey(customer, key);
		var sessions = jdbc.query("""
				select * from orders.checkout_session
				where customer_id = ? and (checkout_session_id = ? or checkout_intent_id = ?)
				order by checkout_session_id for update
				""", (row, index) -> new Session(row.getObject("checkout_session_id", UUID.class),
				row.getObject("checkout_intent_id", UUID.class), row.getString("request_hash"),
				row.getString("checkout_source"), row.getString("status"), row.getString("close_reason"),
				row.getLong("total_amount_vnd"), timestamp(row, "expires_at"), timestamp(row, "paid_at")), customer,
				keySession, intent);
		if (sessions.isEmpty()) {
			return null;
		}
		if (sessions.size() > 1) {
			throw conflict("CHECKOUT_INTENT_CONFLICT");
		}
		Session session = sessions.getFirst();
		if (!session.hash().equals(hash) || !session.intent().equals(intent)) {
			throw conflict(keySession == null ? "CHECKOUT_INTENT_CONFLICT" : "IDEMPOTENCY_KEY_REUSED");
		}
		if (keySession == null) {
			jdbc.update("""
					insert into orders.idempotency_record
					(actor_user_id, operation_context, endpoint, idempotency_key, request_hash, result_ref)
					values (?, 'CUSTOMER', ?, ?, ?, ?) on conflict do nothing
					""", customer, CheckoutCommitRepository.PAY_ENDPOINT, key, hash, session.id());
			if (!session.id().equals(findKey(customer, key))) {
				throw conflict("CHECKOUT_INTENT_CONFLICT");
			}
		}
		return response(session);
	}

	private UUID findKey(UUID customer, String key) {
		return jdbc.queryForList("""
				select result_ref from orders.idempotency_record
				where actor_user_id = ? and operation_context = 'CUSTOMER' and endpoint = ? and idempotency_key = ?
				""", UUID.class, customer, CheckoutCommitRepository.PAY_ENDPOINT, key).stream().findFirst().orElse(null);
	}

	private CheckoutResponse response(Session session) {
		var orders = jdbc.query("""
				select p.*, i.product_name_snapshot, i.cover_media_snapshot, i.item_count
				from orders.purchase_order p
				cross join lateral (
					select product_name_snapshot, cover_media_snapshot, count(*) over () as item_count
					from orders.order_item where order_id = p.order_id order by product_id limit 1
				) i
				where p.checkout_session_id = ? order by p.seller_id
				""", (row, index) -> new Snapshot(new ShippingAddress(row.getString("recipient_name"),
				row.getString("recipient_phone"), row.getString("province_code"), row.getString("province_name"),
				row.getString("ward_code"), row.getString("ward_name"), row.getString("address_detail")),
				new CheckoutResponse.OrderSummary(row.getObject("order_id", UUID.class), session.id(),
						new CheckoutResponse.Seller(row.getObject("seller_id", UUID.class),
								row.getString("seller_name_snapshot")),
						row.getString("status"), row.getLong("total_amount_vnd"), row.getInt("item_count"),
						new CheckoutResponse.FirstItem(row.getString("product_name_snapshot"),
								row.getString("cover_media_snapshot")), timestamp(row, "created_at"), session.paidAt(), null)),
				session.id());
		return new CheckoutResponse(session.id(), session.source(), session.status(), session.closeReason(),
				"NONE", null, session.total(), 0, session.expiresAt(), session.paidAt(), orders.getFirst().address(),
				orders.stream().map(Snapshot::order).toList(), List.of(), true);
	}

	private static Instant timestamp(ResultSet row, String column) throws SQLException {
		OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

	private static BuyNowPayService.Rejected conflict(String code) {
		return new BuyNowPayService.Rejected(409, code);
	}

	private record Session(UUID id, UUID intent, String hash, String source, String status, String closeReason,
			long total, Instant expiresAt, Instant paidAt) {
	}

	private record Snapshot(ShippingAddress address, CheckoutResponse.OrderSummary order) {
	}
}
