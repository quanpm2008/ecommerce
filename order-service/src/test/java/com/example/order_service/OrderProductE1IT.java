package com.example.order_service;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.example.order_service.OrderRealServiceE1Support.required;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderProductE1IT {
	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void orderPreviewUsesRealProductBatch() throws Exception {
		try (var evidence = new OrderRealServiceE1Support("product-order-preview")) {
			labelProductBoundary(evidence);
			evidence.requireInputs("E1_ORDER_URL", "E1_CUSTOMER_BEARER", "E1_PRODUCT_ID");
			UUID productId = UUID.fromString(required("E1_PRODUCT_ID"));
			var response = evidence.request("E1_ORDER_URL", "POST", "/api/v1/orders/checkout/preview",
					required("E1_CUSTOMER_BEARER"), null,
					Map.of("source", "BUY_NOW", "buyNowItem", Map.of("productId", productId, "quantity", 1)), 200);
			var data = response.path("data");
			assertTrue(data.path("canPlaceOrder").asBoolean(), "Preview must accept the eligible in-stock fixture");
			assertTrue(data.path("quoteToken").isTextual() && !data.path("quoteToken").asString().isBlank(),
					"An eligible Preview must issue a quote");
			var line = data.path("orders").path(0).path("items").path(0);
			assertTrue(productId.toString().equals(line.path("productId").asString())
					&& line.path("unitPriceVnd").asLong() > 0, "Preview must contain the fixture product and its VND price");
			evidence.note("Order → Product via BUY_NOW Preview passed");
		}
	}

	@Test
	void realProductAcceptsTheApprovedBatchObject() throws Exception {
		try (var evidence = new OrderRealServiceE1Support("product-batch-contract")) {
			evidence.revision("E1_PRODUCT_REVISION");
			evidence.note("Product provider contract probe. Separate from Order → Product production-path evidence");
			evidence.requireInputs("E1_PRODUCT_URL", "E1_PRODUCT_ID", "E1_PRODUCT_ORDER_SERVICE_TOKEN");
			UUID productId = UUID.fromString(required("E1_PRODUCT_ID"));
			var response = evidence.request("E1_PRODUCT_URL", "POST", "/internal/v1/products/batch",
					null, required("E1_PRODUCT_ORDER_SERVICE_TOKEN"), Map.of("productIds", List.of(productId)), 200);
			var data = response.path("data");
			assertTrue(data.isArray() && data.size() == 1, "Product batch must return exactly the requested fixture");
			var product = data.path(0);
			assertTrue(productId.toString().equals(product.path("productId").asString())
					&& product.path("sellable").asBoolean() && product.path("priceVnd").asLong() > 0
					&& product.path("media").isArray(), "Eligible fixture must carry approved Product batch fields");
			evidence.note("Approved batch object accepted by Product");
		}
	}

	@Test
	void realProductProvisionsAndReadsOrderStock() throws Exception {
		try (var evidence = new OrderRealServiceE1Support("product-order-inventory")) {
			labelProductBoundary(evidence);
			evidence.requireInputs("E1_PRODUCT_PUBLIC_URL", "E1_SELLER_BEARER", "E1_SELLER_ID", "E1_PRODUCT_CREATE_FIXTURE",
					"E1_ORDER_DB_URL", "E1_ORDER_DB_USER", "E1_ORDER_DB_PASSWORD");
			String sellerBearer = required("E1_SELLER_BEARER");
			UUID sellerId = UUID.fromString(required("E1_SELLER_ID"));
			var payload = readCreateFixture();
			int initial = payload.path("initialStock").asInt();
			assertTrue(payload.isObject() && initial > 0 && "SELLING".equals(payload.path("status").asString()),
					"Use a disposable SELLING Product fixture with positive initial stock");
			try (var database = openOrderInventoryReader()) {
				var created = evidence.request("E1_PRODUCT_PUBLIC_URL", "POST", "/api/v1/seller/products",
						sellerBearer, null, payload, 201).path("data");
				UUID productId = UUID.fromString(created.path("productId").asString());
				long version;
				try (var query = database.prepareStatement("""
						select seller_id, on_hand_quantity, reserved_quantity, row_version
						from orders.inventory where product_id = ?
						""")) {
					query.setObject(1, productId);
					try (var row = query.executeQuery()) {
						assertTrue(row.next(), "Real Product create must commit an Order inventory row");
						assertTrue(sellerId.equals(row.getObject("seller_id", UUID.class))
								&& row.getInt("on_hand_quantity") == initial && row.getInt("reserved_quantity") == 0,
								"Provision must use the real Seller and stock specified by the fixture");
						version = row.getLong("row_version");
						assertTrue(!row.next(), "The provision must yield a single stock row");
					}
				}
				evidence.note("Product → Order provision verified with one committed inventory row");
				var publicDetail = evidence.request("E1_PRODUCT_PUBLIC_URL", "GET", "/api/v1/products/" + productId,
						null, null, null, 200).path("data");
				assertTrue(publicDetail.path("availableQuantity").asInt() == initial,
						"Public Product must return positive availability from Order");
				evidence.note("Product → Order availability verified via public detail");
				var sellerDetail = evidence.request("E1_PRODUCT_PUBLIC_URL", "GET", "/api/v1/seller/products/" + productId,
						sellerBearer, null, null, 200).path("data");
				assertTrue(sellerDetail.path("onHandQuantity").asInt() == initial
						&& sellerDetail.path("reservedQuantity").isIntegralNumber()
						&& sellerDetail.path("reservedQuantity").asInt() == 0
						&& sellerDetail.path("availableQuantity").asInt() == initial
						&& sellerDetail.path("inventoryVersion").isIntegralNumber()
						&& sellerDetail.path("inventoryVersion").asLong() == version,
						"Seller Product detail must agree with the committed Order stock and version");
				evidence.note("Product → Order inventory details verified via Seller detail");
				evidence.note("No Product provision retry or intermediate HIDDEN transition observed by this fixture");
			} catch (SQLException unavailable) {
				throw new IllegalStateException("Order inventory reader failed with SQL state " + unavailable.getSQLState());
			}
		}
	}

	private static void labelProductBoundary(OrderRealServiceE1Support evidence) {
		evidence.revision("E1_ORDER_REVISION");
		evidence.revision("E1_PRODUCT_REVISION");
		evidence.requireInputs("E1_IDENTITY_KIND");
		String identityKind = required("E1_IDENTITY_KIND");
		evidence.scope("Product real, Identity peer", identityKind);
		if ("real".equals(identityKind)) {
			evidence.revision("E1_IDENTITY_REVISION");
		}
	}

	private static JsonNode readCreateFixture() {
		try (var input = Files.newInputStream(Path.of(required("E1_PRODUCT_CREATE_FIXTURE")))) {
			return JSON.readTree(input);
		} catch (IOException | JacksonException invalid) {
			throw new IllegalStateException("Could not read the E1_PRODUCT_CREATE_FIXTURE JSON");
		}
	}

	private static Connection openOrderInventoryReader() throws SQLException {
		var connection = DriverManager.getConnection(required("E1_ORDER_DB_URL"),
				required("E1_ORDER_DB_USER"), required("E1_ORDER_DB_PASSWORD"));
		try {
			connection.setReadOnly(true);
			try (var statement = connection.createStatement();
					var result = statement.executeQuery("select current_database()")) {
				assertTrue(result.next() && result.getString(1).startsWith("order_test_"),
						"Use an isolated order_test_ database for the disposable Product fixture");
			}
			return connection;
		} catch (SQLException | RuntimeException | AssertionError failure) {
			connection.close();
			throw failure;
		}
	}
}
