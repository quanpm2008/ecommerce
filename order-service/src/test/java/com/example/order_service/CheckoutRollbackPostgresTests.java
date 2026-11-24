package com.example.order_service;

import com.example.order_service.repository.CheckoutCommitRepository;
import com.example.order_service.repository.CheckoutCommitRepository.Line;
import com.example.order_service.repository.CheckoutCommitRepository.NewCheckout;
import com.example.order_service.client.PayIdentityClient.ShippingAddress;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckoutRollbackPostgresTests extends PayHttpTestSupport {
	private static final List<String> EFFECT_TABLES = List.of("checkout_session", "purchase_order", "order_item",
			"reservation", "idempotency_record", "checkout_outbox", "stock_movement");
	private static final ShippingAddress ADDRESS = new ShippingAddress("Khách thử", "0900000000", "01", "Tỉnh thử",
			"001", "Phường thử", "Địa chỉ thử");

	@Autowired
	CheckoutCommitRepository commits;

	@Test
	void rollsBackTheFirstProductHoldWhenTheLaterLineDoesNotHaveStock() {
		var a = sample(3, 2);
		var b = sample(3, 2);
		var later = a.product().compareTo(b.product()) < 0 ? b : a;
		jdbc.update("update orders.inventory set on_hand_quantity = 0 where product_id = ?", later.product());
		var counts = effectCounts();
		var oldStock = stock(a.product(), b.product());
		var request = checkout(a, "missing-later-line", List.of(line(b), line(a)));
		assertThatThrownBy(() -> commits.commit(request)).satisfies(
				failure -> assertThat(failure.getClass().getSimpleName()).isEqualTo("StockUnavailable"));
		assertThat(stock(a.product(), b.product())).isEqualTo(oldStock);
		assertThat(effectCounts()).isEqualTo(counts);
		assertThat(lastTouch.get()).isNull();
	}

	@Test
	void rejectsOneSoldOutBuyNowWithoutPartialEffects() throws Exception {
		var sample = sample(0, 1);
		var counts = effectCounts();
		var oldStock = stock(sample.product(), sample.product());
		assertRejected(pay(sample.customer(), UUID.randomUUID(), quote(sample), sample.address()), 409, "CHECKOUT_ITEMS_INVALID");
		assertThat(stock(sample.product(), sample.product())).isEqualTo(oldStock);
		assertThat(effectCounts()).isEqualTo(counts);
		assertThat(lastTouch.get()).isNull();
	}

	@Test
	void rollsBackHoldOrderAndReservationWhenPostgresRejectsTheIdempotencyInsert() {
		var sample = sample(3, 2);
		var counts = effectCounts();
		var oldStock = stock(sample.product(), sample.product());
		jdbc.execute("""
				create function orders.reject_phase1_rollback_fixture() returns trigger language plpgsql as $$
				begin
				    if new.request_hash = 'phase1-forced-failure' then
				        if not exists (select 1 from orders.reservation r join orders.order_item i using (order_item_id)
				                join orders.purchase_order p using (order_id) join orders.inventory n on n.product_id = r.product_id
				                where p.checkout_session_id = new.result_ref and r.status = 'HELD'
				                and n.reserved_quantity = r.quantity and n.row_version = 1) then
				            raise exception 'fixture did not observe the hold';
				        end if;
				        raise exception 'fixture forced rollback after hold' using errcode = '23514';
				    end if;
				    return new;
				end $$;
				""");
		try {
			jdbc.execute("""
					create trigger reject_phase1_rollback_fixture before insert on orders.idempotency_record
					for each row execute function orders.reject_phase1_rollback_fixture()
					""");
			assertThatThrownBy(() -> commits.commit(checkout(sample, "phase1-forced-failure", List.of(line(sample)))))
					.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fixture forced rollback after hold");
			assertThat(stock(sample.product(), sample.product())).isEqualTo(oldStock);
			assertThat(effectCounts()).isEqualTo(counts);
			assertThat(lastTouch.get()).isNull();
		} finally {
			jdbc.execute("drop trigger if exists reject_phase1_rollback_fixture on orders.idempotency_record");
			jdbc.execute("drop function orders.reject_phase1_rollback_fixture()");
		}
	}

	private Map<String, Integer> effectCounts() {
		return EFFECT_TABLES.stream().collect(Collectors.toMap(table -> table,
				table -> jdbc.queryForObject("select count(*) from orders." + table, Integer.class)));
	}

	private List<Map<String, Object>> stock(UUID first, UUID second) {
		return jdbc.queryForList("""
				select product_id, on_hand_quantity, reserved_quantity, row_version
				from orders.inventory where product_id in (?, ?) order by product_id
				""", first, second);
	}

	private static NewCheckout checkout(Sample sample, String hash, List<Line> lines) {
		return new NewCheckout(sample.customer(), UUID.randomUUID(), UUID.randomUUID().toString(), hash,
				sample.address(), ADDRESS, 500, lines);
	}

	private static Line line(Sample sample) {
		return new Line(sample.product(), SELLER, "Shop thử", "Sản phẩm thử", "https://example.invalid/test.png",
				1000L, sample.quantity());
	}
}
