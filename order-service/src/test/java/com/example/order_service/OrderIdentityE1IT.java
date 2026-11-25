package com.example.order_service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static com.example.order_service.OrderRealServiceE1Support.required;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderIdentityE1IT {
	private static final String IDENTITY = "E1_IDENTITY_URL";
	private static final String ORDER = "E1_ORDER_URL";
	private static final String INTERNAL = "/internal/v1/identity";
	private static final String PUBLIC = "/api/v1/identity";
	private static final String ADDRESSES = PUBLIC + "/customers/me/addresses";
	private static final String PREVIEW = "/api/v1/orders/checkout/preview";
	private static final String PAY = "/api/v1/orders/checkout/pay";

	@Test
	void verifiesIdentityBoundaryAndCheckoutThroughDeployedOrder() throws Exception {
		try (var run = new OrderRealServiceE1Support("identity-boundary")) {
			run.requireInputs(IDENTITY, ORDER, "E1_PRODUCT_KIND", "E1_CUSTOMER_BEARER", "E1_IDENTITY_SERVICE_TOKEN",
					"E1_CUSTOMER_ID", "E1_OTHER_CUSTOMER_ID", "E1_SELLER_ID", "E1_ADDRESS_ID", "E1_PRODUCT_ID",
					"E1_STALE_CUSTOMER_BEARER", "E1_REVOKED_CUSTOMER_BEARER", "E1_BANNED_CUSTOMER_BEARER");
			run.revision("E1_ORDER_REVISION");
			run.revision("E1_IDENTITY_REVISION");
			String productKind = required("E1_PRODUCT_KIND");
			run.scope("Order/Identity", productKind);
			if ("real".equals(productKind)) {
				run.revision("E1_PRODUCT_REVISION");
			}
			run.note("Identity=real; Product=" + productKind + "; Finance=HTTP mock");

			String bearer = required("E1_CUSTOMER_BEARER");
			String serviceToken = required("E1_IDENTITY_SERVICE_TOKEN");
			UUID customer = fixtureId("E1_CUSTOMER_ID");
			UUID otherCustomer = fixtureId("E1_OTHER_CUSTOMER_ID");
			assertTrue(!customer.equals(otherCustomer), "Expected two distinct isolated E1 Customer fixtures");
			UUID seller = fixtureId("E1_SELLER_ID");
			UUID address = fixtureId("E1_ADDRESS_ID");
			UUID product = fixtureId("E1_PRODUCT_ID");
			String stale = required("E1_STALE_CUSTOMER_BEARER");
			String revoked = required("E1_REVOKED_CUSTOMER_BEARER");
			String banned = required("E1_BANNED_CUSTOMER_BEARER");
			String addressPath = INTERNAL + "/customers/" + customer + "/addresses/" + address;
			var preview = Map.of("source", "BUY_NOW", "buyNowItem", Map.of("productId", product, "quantity", 1));

			assertAll("Independent Identity boundary checks",
					() -> authorization(run, bearer, serviceToken, customer),
					() -> referenceData(run, bearer, serviceToken, customer, otherCustomer, seller, address, addressPath),
					() -> assertAll("Order checks live Identity authorization",
							() -> denied(run, stale, preview, 401, "TOKEN_STALE"),
							() -> denied(run, revoked, preview, 401, "UNAUTHENTICATED"),
							() -> denied(run, banned, preview, 403, "ACCOUNT_BANNED")),
					() -> checkout(run, bearer, serviceToken, seller, address, addressPath, preview));
		}
	}

	private static void authorization(OrderRealServiceE1Support run, String bearer, String token, UUID customer)
			throws Exception {
		run.note("API 7.1 authorization context");
		JsonNode auth = run.request(IDENTITY, "POST", INTERNAL + "/auth/authorization-context", bearer, token, null, 200)
				.path("data");
		assertTrue(customer.toString().equals(auth.path("userId").asString()), "Authorization context Customer mismatch");
		assertTrue("CUSTOMER".equals(auth.path("accountType").asString()), "Expected CUSTOMER account type");
		assertTrue("ACTIVE".equals(auth.path("accountStatus").asString()), "Expected ACTIVE account status");
		assertTrue("ACTIVE".equals(auth.path("userStatus").asString()), "Expected ACTIVE user status");
		assertTrue("FULL".equals(auth.path("accessMode").asString()), "Expected FULL access mode");
		assertTrue(auth.path("sessionActive").asBoolean(), "Expected active fixture session");
		assertTrue(auth.has("bannedAt") && auth.has("disabledAt") && auth.has("restoreUntil"),
				"Authorization context lacks lifecycle fields");
		assertTrue(StreamSupport.stream(auth.path("permissions").spliterator(), false)
				.anyMatch(permission -> "checkout:create:own".equals(permission.asString())), "Expected checkout permission");
	}

	private static void referenceData(OrderRealServiceE1Support run, String bearer, String token, UUID customer,
			UUID otherCustomer, UUID seller, UUID address, String addressPath) throws Exception {
		run.note("API 7.1 checkout eligibility, Seller status and shipping ownership");
		JsonNode eligible = run.request(IDENTITY, "GET", INTERNAL + "/customers/" + customer + "/checkout-eligibility",
				null, token, null, 200).path("data");
		assertTrue(customer.toString().equals(eligible.path("userId").asString()), "Eligibility Customer mismatch");
		assertTrue(eligible.path("canCheckout").asBoolean(), "Expected an eligible Customer fixture");
		JsonNode status = run.request(IDENTITY, "GET", INTERNAL + "/sellers/status?ids=" + seller, null, token, null, 200)
				.path("data").path(0);
		assertTrue(seller.toString().equals(status.path("sellerId").asString()), "Seller status ID mismatch");
		assertTrue(status.path("sellable").asBoolean(), "Expected a sellable Seller fixture");
		JsonNode snapshot = run.request(IDENTITY, "GET", addressPath, null, token, null, 200).path("data");
		for (String field : List.of("recipientName", "recipientPhone", "provinceCode", "provinceName", "wardCode",
				"wardName", "addressDetail")) {
			assertTrue(!snapshot.path(field).asString().isBlank(), "Incomplete shipping address: " + field);
		}
		run.request(IDENTITY, "GET", INTERNAL + "/customers/" + otherCustomer + "/addresses/" + address,
				null, token, null, 404);

		run.note("API 6.1 default address and public Seller name");
		JsonNode defaultAddress = findAddress(run, bearer, address);
		assertTrue(defaultAddress.path("isDefault").asBoolean(), "Expected disposable fixture address to be used by Preview");
		JsonNode publicSeller = run.request(IDENTITY, "GET", PUBLIC + "/sellers/" + seller, null, null, null, 200)
				.path("data");
		assertTrue(seller.toString().equals(publicSeller.path("sellerId").asString()), "Public Seller ID mismatch");
		assertTrue(!publicSeller.path("sellerName").asString().isBlank(), "Public Seller name missing");
	}

	private static void denied(OrderRealServiceE1Support run, String bearer, Object preview, int status, String code)
			throws Exception {
		run.note("Order authorization fixture expected " + code);
		var error = run.request(ORDER, "POST", PREVIEW, bearer, null, preview, status).path("error");
		assertTrue(code.equals(error.path("code").asString()), "Unexpected Order error for " + code);
	}

	private static void checkout(OrderRealServiceE1Support run, String bearer, String token, UUID seller, UUID address,
			String addressPath, Object previewRequest) throws Exception {
		run.note("API 6.4 Preview and Pay, then verify API 7.1 post-commit address touch");
		assertTrue(findAddress(run, bearer, address).path("lastUsedAt").isNull(),
				"Expected a fresh disposable fixture address with no prior use");
		JsonNode publicSeller = run.request(IDENTITY, "GET", PUBLIC + "/sellers/" + seller, null, null, null, 200)
				.path("data");
		JsonNode preview = run.request(ORDER, "POST", PREVIEW, bearer, null, previewRequest, 200).path("data");
		assertTrue(preview.path("canPlaceOrder").asBoolean(), "Expected purchasable and in-stock BUY_NOW fixture");
		assertTrue(address.toString().equals(preview.path("defaultAddress").path("addressId").asString()),
				"Preview did not use the Identity default address");
		var previewSeller = preview.path("orders").path(0).path("seller");
		assertTrue(seller.toString().equals(previewSeller.path("sellerId").asString())
				&& previewSeller.path("sellerName").equals(publicSeller.path("sellerName")),
				"Preview did not include the expected public Identity Seller name");
		String quote = preview.path("quoteToken").asString();
		assertTrue(!quote.isBlank(), "Expected a quote for this BUY_NOW fixture");
		var pay = Map.of("quoteToken", quote, "addressId", address);
		var key = Map.of("Idempotency-Key", UUID.randomUUID().toString());
		JsonNode pending = run.request(ORDER, "POST", PAY, bearer, null, pay, 202, key).path("data");
		assertTrue("AWAITING_PAYMENT".equals(pending.path("status").asString()), "Expected a committed pending checkout");
		String session = pending.path("checkoutSessionId").asString();
		assertTrue(!session.isBlank(), "Checkout response lacks a session ID");
		String touchedAt = findAddress(run, bearer, address).path("lastUsedAt").asString();
		assertTrue(!touchedAt.isBlank(), "Pay did not touch the real Identity address after commit");
		run.request(IDENTITY, "POST", addressPath + "/touch", null, token, Map.of("checkoutSessionId", session), 200);
		assertTrue(touchedAt.equals(findAddress(run, bearer, address).path("lastUsedAt").asString()),
				"Repeating the same checkout address touch changed lastUsedAt");

		run.note("API 6.1 delete disposable fixture address; API 6.4 replay saved address snapshot");
		run.request(IDENTITY, "DELETE", ADDRESSES + "/" + address, bearer, null, null, 204);
		run.request(IDENTITY, "GET", addressPath, null, token, null, 404);
		var deletedTouch = run.request(IDENTITY, "POST", addressPath + "/touch", null, token,
				Map.of("checkoutSessionId", session), 200).path("data");
		assertTrue(!deletedTouch.path("applied").asBoolean(), "Deleted address touch must be a no-op");
		JsonNode replay = run.request(ORDER, "POST", PAY, bearer, null, pay, 202, key).path("data");
		assertTrue(session.equals(replay.path("checkoutSessionId").asString()), "Replay returned a different checkout");
		assertTrue(pending.path("shippingAddress").isObject()
				&& pending.path("shippingAddress").equals(replay.path("shippingAddress")),
				"Replay failed to preserve the address snapshot after Identity deletion");
	}

	private static JsonNode findAddress(OrderRealServiceE1Support run, String bearer, UUID address) throws Exception {
		var book = run.request(IDENTITY, "GET", ADDRESSES, bearer, null, null, 200).path("data");
		return StreamSupport.stream(book.spliterator(), false)
				.filter(item -> address.toString().equals(item.path("addressId").asString())).findFirst()
				.orElseThrow(() -> new AssertionError("Disposable E1 address not present in the Identity address book"));
	}

	private static UUID fixtureId(String name) {
		try {
			return UUID.fromString(required(name));
		} catch (IllegalArgumentException ignored) {
			throw new AssertionError("Invalid test fixture UUID in " + name);
		}
	}
}
