package com.example.cart_service.cart;

import com.example.cart_service.FakeRemotes;
import com.example.cart_service.IntegrationTestSupport;
import com.example.cart_service.cart.domain.CartStore;
import com.example.cart_service.common.error.ApiException;
import com.example.cart_service.common.error.ErrorCode;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-B03 / API §6.3 / P-10 / AC-ORD-001 through the HTTP API. */
class CartEndpointsTests extends IntegrationTestSupport {

	private static final UUID SHOP_A = UUID.randomUUID();

	private static final UUID SHOP_B = UUID.randomUUID();

	private static final UUID SHIRT = UUID.randomUUID();

	private static final UUID JEANS = UUID.randomUUID();

	private static final UUID HAT = UUID.randomUUID();

	@Autowired
	private CartStore store;

	private UUID customerId;

	private String token;

	@BeforeEach
	void catalog() {
		remotes.seller(SHOP_A, "Shop Áo Xinh", true);
		remotes.seller(SHOP_B, "Shop Quần", true);
		remotes.product(SHIRT, SHOP_A, "Áo thun basic cotton", 150_000, "SELLING", 48);
		remotes.product(JEANS, SHOP_B, "Quần jean slim", 320_000, "SELLING", 2);
		remotes.product(HAT, SHOP_A, "Mũ lưỡi trai", 90_000, "SELLING", 0);
		customerId = UUID.randomUUID();
		token = remotes.activeCustomer(customerId);
	}

	// ---- access (API §3.1, UC-B03.PRE-01, UC-B03.ALT-06) ----

	@Test
	void requiresLogin() throws Exception {
		mvc.perform(get("/api/v1/cart")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
		mvc.perform(get("/api/v1/cart/summary").header(HttpHeaders.AUTHORIZATION, "Basic abc"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void passesIdentityTokenErrorsOn() throws Exception {
		mvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + remotes.tokenFailing(ErrorCode.TOKEN_STALE)))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("TOKEN_STALE"));
		mvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + remotes.tokenFailing(ErrorCode.TOKEN_EXPIRED)))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));
	}

	@Test
	void bannedCustomerCannotUseTheCart() throws Exception {
		String banned = remotes.token(FakeRemotes.context(UUID.randomUUID(), "ACTIVE", "BANNED",
				List.of("cart:manage:own", "order:read:own")));
		mvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + banned))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_BANNED"));
		mvc.perform(post("/api/v1/cart/items").header(HttpHeaders.AUTHORIZATION, "Bearer " + banned)
				.contentType(MediaType.APPLICATION_JSON).content(addBody(SHIRT, 1)))
				.andExpect(status().isForbidden());
	}

	@Test
	void sellerOrAdminAccountWithoutCartPermissionIsForbidden() throws Exception {
		String seller = remotes.token(FakeRemotes.context(UUID.randomUUID(), "ACTIVE", null,
				List.of("product:manage:own", "order:fulfill:own")));
		mvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + seller))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
	}

	@Test
	void identityOutageIsServiceUnavailableNotAPass() throws Exception {
		remotes.identityUp = false;
		mvc.perform(get("/api/v1/cart/summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error.code").value("SERVICE_UNAVAILABLE"));
	}

	@Test
	void echoesRequestIdInErrors() throws Exception {
		mvc.perform(get("/api/v1/cart").header("X-Request-Id", "req-123"))
				.andExpect(status().isUnauthorized()).andExpect(header().string("X-Request-Id", "req-123"))
				.andExpect(jsonPath("$.error.requestId").value("req-123"));
	}

	// ---- view, grouping and line status (UC-B03.MAIN-02, Database §3.3.2) ----

	@Test
	void emptyCart() throws Exception {
		as(get("/api/v1/cart")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.cartId").isNotEmpty())
				.andExpect(jsonPath("$.data.groups", hasSize(0)))
				.andExpect(jsonPath("$.data.removedItems", hasSize(0)))
				.andExpect(jsonPath("$.data.summary.totalQuantity").value(0))
				.andExpect(jsonPath("$.data.summary.lineCount").value(0))
				.andExpect(jsonPath("$.data.paymentBlocked").value(false));
		as(get("/api/v1/cart/summary")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.lineCount").value(0))
				.andExpect(jsonPath("$.data.totalQuantity").value(0));
	}

	@Test
	void groupsLinesBySellerWithCurrentPriceAndStockStatus() throws Exception {
		add(SHIRT, 2).andExpect(status().isOk());
		add(JEANS, 3).andExpect(status().isOk());
		add(HAT, 1).andExpect(status().isOk());

		as(get("/api/v1/cart")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.groups", hasSize(2)))
				.andExpect(jsonPath("$.data.groups[0].seller.sellerId").value(SHOP_A.toString()))
				.andExpect(jsonPath("$.data.groups[0].seller.sellerName").value("Shop Áo Xinh"))
				.andExpect(jsonPath("$.data.groups[0].items", hasSize(2)))
				.andExpect(jsonPath("$.data.groups[0].items[0].productId").value(SHIRT.toString()))
				.andExpect(jsonPath("$.data.groups[0].items[0].productName").value("Áo thun basic cotton"))
				.andExpect(jsonPath("$.data.groups[0].items[0].unitPriceVnd").value(150_000))
				.andExpect(jsonPath("$.data.groups[0].items[0].quantity").value(2))
				.andExpect(jsonPath("$.data.groups[0].items[0].availableQuantity").value(48))
				.andExpect(jsonPath("$.data.groups[0].items[0].lineStatus").value("IN_STOCK"))
				.andExpect(jsonPath("$.data.groups[0].items[0].selectable").value(true))
				.andExpect(jsonPath("$.data.groups[0].items[0].lineTotalVnd").value(300_000))
				.andExpect(jsonPath("$.data.groups[0].items[1].productId").value(HAT.toString()))
				.andExpect(jsonPath("$.data.groups[0].items[1].lineStatus").value("OUT_OF_STOCK"))
				.andExpect(jsonPath("$.data.groups[0].items[1].selectable").value(false))
				.andExpect(jsonPath("$.data.groups[1].seller.sellerId").value(SHOP_B.toString()))
				.andExpect(jsonPath("$.data.groups[1].items[0].lineStatus").value("INSUFFICIENT_STOCK"))
				.andExpect(jsonPath("$.data.groups[1].items[0].availableQuantity").value(2))
				.andExpect(jsonPath("$.data.groups[1].items[0].selectable").value(false))
				.andExpect(jsonPath("$.data.summary.totalQuantity").value(6))
				.andExpect(jsonPath("$.data.summary.lineCount").value(3));

		as(get("/api/v1/cart/summary")).andExpect(jsonPath("$.data.lineCount").value(3))
				.andExpect(jsonPath("$.data.totalQuantity").value(6));
	}

	@Test
	void priceAndStockAreReadFreshOnEveryView() throws Exception {
		add(JEANS, 3);
		remotes.stock(JEANS, 10);
		remotes.product(JEANS, SHOP_B, "Quần jean slim", 299_000, "SELLING", 10);
		as(get("/api/v1/cart")).andExpect(jsonPath("$.data.groups[0].items[0].unitPriceVnd").value(299_000))
				.andExpect(jsonPath("$.data.groups[0].items[0].lineStatus").value("IN_STOCK"))
				.andExpect(jsonPath("$.data.groups[0].items[0].lineTotalVnd").value(897_000));
	}

	@Test
	void unsellableProductIsRemovedWhenTheCartIsOpened() throws Exception {
		add(SHIRT, 1);
		add(HAT, 1);
		remotes.status(HAT, "HIDDEN");

		as(get("/api/v1/cart")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.removedItems", hasSize(1)))
				.andExpect(jsonPath("$.data.removedItems[0].productId").value(HAT.toString()))
				.andExpect(jsonPath("$.data.removedItems[0].productName").value("Mũ lưỡi trai"))
				.andExpect(jsonPath("$.data.removedItems[0].reason").value("PRODUCT_NOT_SELLABLE"))
				.andExpect(jsonPath("$.data.summary.lineCount").value(1));
		// Removed for good: the next view no longer reports it.
		as(get("/api/v1/cart")).andExpect(jsonPath("$.data.removedItems", hasSize(0)))
				.andExpect(jsonPath("$.data.summary.lineCount").value(1));
	}

	@Test
	void productOfBannedSellerIsRemovedToo() throws Exception {
		add(JEANS, 1);
		remotes.seller(SHOP_B, "Shop Quần", false);
		as(get("/api/v1/cart")).andExpect(jsonPath("$.data.removedItems[0].productId").value(JEANS.toString()))
				.andExpect(jsonPath("$.data.groups", hasSize(0)));
	}

	@Test
	void sourceOutageIsServiceUnavailableAndRemovesNothing() throws Exception {
		add(SHIRT, 1);
		remotes.productUp = false;
		as(get("/api/v1/cart")).andExpect(status().isServiceUnavailable());
		remotes.productUp = true;
		remotes.inventoryUp = false;
		as(get("/api/v1/cart")).andExpect(status().isServiceUnavailable());
		remotes.inventoryUp = true;
		as(get("/api/v1/cart")).andExpect(jsonPath("$.data.summary.lineCount").value(1))
				.andExpect(jsonPath("$.data.removedItems", hasSize(0)));
	}

	// ---- add (P-10, API §6.3 POST /cart/items) ----

	@Test
	void addingTheSameProductAddsToItsLine() throws Exception {
		String first = add(SHIRT, 2).andReturn().getResponse().getContentAsString();
		add(SHIRT, 3).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.groups[0].items", hasSize(1)))
				.andExpect(jsonPath("$.data.groups[0].items[0].quantity").value(5))
				.andExpect(jsonPath("$.data.groups[0].items[0].cartItemId")
						.value((String) JsonPath.read(first, "$.data.groups[0].items[0].cartItemId")));
	}

	@Test
	void lineHoldsAtMost99Units() throws Exception {
		add(SHIRT, 98).andExpect(status().isOk());
		add(SHIRT, 1).andExpect(status().isOk()).andExpect(jsonPath("$.data.groups[0].items[0].quantity").value(99));
		add(SHIRT, 1).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("QUANTITY_LIMIT_EXCEEDED"))
				.andExpect(jsonPath("$.error.details[0].issue").value("MAX_PER_LINE"))
				.andExpect(jsonPath("$.error.details[0].currentQuantity").value(99));
		add(JEANS, 100).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("QUANTITY_LIMIT_EXCEEDED"));
		add(JEANS, 0).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error.details[0].field").value("quantity"));
		as(get("/api/v1/cart/summary")).andExpect(jsonPath("$.data.lineCount").value(1))
				.andExpect(jsonPath("$.data.totalQuantity").value(99));
	}

	@Test
	void cartHoldsAtMost50DifferentProducts() throws Exception {
		UUID first = null;
		for (int i = 0; i < 50; i++) {
			UUID product = UUID.randomUUID();
			first = first == null ? product : first;
			remotes.product(product, SHOP_A, "P" + i, 1_000, "SELLING", 5);
			add(product, 1).andExpect(status().isOk());
		}
		UUID fiftyFirst = UUID.randomUUID();
		remotes.product(fiftyFirst, SHOP_A, "P50", 1_000, "SELLING", 5);
		add(fiftyFirst, 1).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("QUANTITY_LIMIT_EXCEEDED"))
				.andExpect(jsonPath("$.error.details[0].issue").value("MAX_LINES"));
		// An existing line can still grow.
		add(first, 1).andExpect(status().isOk());
		as(get("/api/v1/cart/summary")).andExpect(jsonPath("$.data.lineCount").value(50))
				.andExpect(jsonPath("$.data.totalQuantity").value(51));
	}

	@Test
	void outOfStockProductCanBeAddedButNotAnUnsellableOne() throws Exception {
		add(HAT, 1).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.groups[0].items[0].lineStatus").value("OUT_OF_STOCK"));
		UUID hidden = UUID.randomUUID();
		remotes.product(hidden, SHOP_A, "Hidden", 1_000, "HIDDEN", 5);
		add(hidden, 1).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_SELLABLE"));
		add(UUID.randomUUID(), 1).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_SELLABLE"));
		remotes.seller(SHOP_B, "Shop Quần", false);
		add(JEANS, 1).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_SELLABLE"));
	}

	@Test
	void addValidatesTheBody() throws Exception {
		as(post("/api/v1/cart/items").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error.details[0].field").value("productId"));
		as(post("/api/v1/cart/items").contentType(MediaType.APPLICATION_JSON).content("not json"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void outageDuringAddWritesNothing() throws Exception {
		remotes.inventoryUp = false;
		add(SHIRT, 1).andExpect(status().isServiceUnavailable());
		remotes.inventoryUp = true;
		as(get("/api/v1/cart/summary")).andExpect(jsonPath("$.data.lineCount").value(0));
	}

	// ---- change quantity and remove ----

	@Test
	void changeQuantityAndRemoveALine() throws Exception {
		String body = add(SHIRT, 2).andReturn().getResponse().getContentAsString();
		String line = JsonPath.read(body, "$.data.groups[0].items[0].cartItemId");
		add(JEANS, 1);

		as(patch("/api/v1/cart/items/" + line).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":7}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.groups[0].items[0].quantity").value(7))
				.andExpect(jsonPath("$.data.summary.totalQuantity").value(8));
		as(patch("/api/v1/cart/items/" + line).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":100}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("QUANTITY_LIMIT_EXCEEDED"));
		as(patch("/api/v1/cart/items/" + line).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}"))
				.andExpect(status().isBadRequest());

		as(delete("/api/v1/cart/items/" + line)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.groups", hasSize(1)))
				.andExpect(jsonPath("$.data.groups[0].items[0].productId").value(JEANS.toString()));
		as(delete("/api/v1/cart/items/" + line)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void removedThenReAddedProductGetsANewLine() throws Exception {
		String body = add(SHIRT, 2).andReturn().getResponse().getContentAsString();
		String line = JsonPath.read(body, "$.data.groups[0].items[0].cartItemId");
		as(delete("/api/v1/cart/items/" + line)).andExpect(status().isOk());
		String again = add(SHIRT, 1).andReturn().getResponse().getContentAsString();
		assertThat((String) JsonPath.read(again, "$.data.groups[0].items[0].cartItemId")).isNotEqualTo(line);
	}

	@Test
	void anotherCustomersLineIsNotFound() throws Exception {
		String body = add(SHIRT, 2).andReturn().getResponse().getContentAsString();
		String line = JsonPath.read(body, "$.data.groups[0].items[0].cartItemId");
		String other = remotes.activeCustomer(UUID.randomUUID());

		mvc.perform(patch("/api/v1/cart/items/" + line).header(HttpHeaders.AUTHORIZATION, "Bearer " + other)
				.contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":5}"))
				.andExpect(status().isNotFound());
		mvc.perform(delete("/api/v1/cart/items/" + line).header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
				.andExpect(jsonPath("$.data.groups", hasSize(0)));
		as(get("/api/v1/cart")).andExpect(jsonPath("$.data.groups[0].items[0].quantity").value(2));
		as(delete("/api/v1/cart/items/not-a-uuid")).andExpect(status().isBadRequest());
	}

	@Test
	void sellerNameIsOptionalForDisplay() throws Exception {
		UUID shop = UUID.randomUUID();
		UUID product = UUID.randomUUID();
		remotes.seller(shop, null, true);
		remotes.product(product, shop, "Nameless shop item", 1_000, "SELLING", 3);
		add(product, 1).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.groups[0].seller.sellerName").value(nullValue()));
	}

	// ---- concurrency (Database §3.3.2: limits hold for concurrent adds) ----

	@Test
	void concurrentAddsCannotPassThe50LineLimit() throws Exception {
		for (int i = 0; i < 49; i++) {
			store.add(customerId, UUID.randomUUID(), 1);
		}
		List<Callable<Boolean>> tasks = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			UUID product = UUID.randomUUID();
			tasks.add(() -> tryAdd(product, 1));
		}
		long succeeded = runTogether(tasks).stream().filter(Boolean::booleanValue).count();
		assertThat(succeeded).isEqualTo(1);
		assertThat(store.totals(customerId).lineCount()).isEqualTo(50);
	}

	@Test
	void concurrentAddsOfTheSameProductCannotPass99() throws Exception {
		List<Callable<Boolean>> tasks = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			tasks.add(() -> tryAdd(SHIRT, 10));
		}
		long succeeded = runTogether(tasks).stream().filter(Boolean::booleanValue).count();
		assertThat(succeeded).isEqualTo(9);
		assertThat(store.totals(customerId).lineCount()).isEqualTo(1);
		assertThat(store.totals(customerId).totalQuantity()).isEqualTo(90);
	}

	private boolean tryAdd(UUID productId, int quantity) {
		try {
			store.add(customerId, productId, quantity);
			return true;
		}
		catch (ApiException ex) {
			assertThat(ex.code()).isEqualTo(ErrorCode.QUANTITY_LIMIT_EXCEEDED);
			return false;
		}
	}

	private static List<Boolean> runTogether(List<Callable<Boolean>> tasks) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Boolean>> futures = new ArrayList<>();
			for (Callable<Boolean> task : tasks) {
				futures.add(pool.submit(() -> {
					start.await();
					return task.call();
				}));
			}
			start.countDown();
			List<Boolean> results = new ArrayList<>();
			for (Future<Boolean> future : futures) {
				results.add(future.get());
			}
			return results;
		}
		finally {
			pool.shutdownNow();
		}
	}

	// ---- helpers ----

	private ResultActions add(UUID productId, int quantity) throws Exception {
		return as(post("/api/v1/cart/items").contentType(MediaType.APPLICATION_JSON)
				.content(addBody(productId, quantity)));
	}

	private static String addBody(UUID productId, int quantity) {
		return "{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}";
	}

	private ResultActions as(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
			throws Exception {
		return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

}
