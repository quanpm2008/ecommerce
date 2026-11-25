package com.example.order_service;

import com.example.order_service.service.InventoryProvisionService;
import com.example.order_service.service.InventoryProvisionService.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "eureka.client.enabled=false")
class InventoryProvisionPostgresTests extends OrderPostgresTestSupport {
	@Autowired
	private InventoryProvisionService provision;
	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void concurrentSameOwnerCreatesExactlyOneRow() throws Exception {
		UUID product = UUID.randomUUID();
		UUID seller = UUID.randomUUID();
		var start = new CyclicBarrier(2);
		Callable<Result> request = () -> {
			start.await(5, TimeUnit.SECONDS);
			return provision.provision(product, seller, 10);
		};
		try (var threads = Executors.newFixedThreadPool(2)) {
			var first = threads.submit(request);
			var second = threads.submit(request);
			assertThat(new Result[]{first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)})
					.containsExactlyInAnyOrder(Result.CREATED, Result.EXISTS);
		}
		assertThat(jdbc.queryForList("select seller_id from orders.inventory where product_id = ?", UUID.class, product))
				.containsExactly(seller);
	}

	@Test
	void replayAndOwnerConflictPreserveCommittedStockAndHistory() {
		UUID product = UUID.randomUUID();
		UUID seller = UUID.randomUUID();
		assertThat(provision.provision(product, seller, 10)).isEqualTo(Result.CREATED);
		jdbc.update("update orders.inventory set on_hand_quantity = 25, reserved_quantity = 4, row_version = 3 where product_id = ?", product);
		var before = jdbc.queryForMap("select * from orders.inventory where product_id = ?", product);
		assertThat(provision.provision(product, seller, 99)).isEqualTo(Result.EXISTS);
		assertThat(provision.provision(product, UUID.randomUUID(), 25)).isEqualTo(Result.OWNER_CONFLICT);
		assertThat(jdbc.queryForMap("select * from orders.inventory where product_id = ?", product)).isEqualTo(before);
		assertThat(jdbc.queryForObject("select count(*) from orders.stock_movement where product_id = ?", Integer.class, product))
				.isZero();
	}
}
