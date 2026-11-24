package com.example.order_service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.example.order_service.Phase2LocalBrokerSupport.JSON;

/** Cart's HTTP and persistence contract for the broker test. */
final class CartCheckoutBrokerFixture implements AutoCloseable {
    static final String TOKEN = "phase5-order-to-cart";

    private final Phase2LocalBrokerSupport stack;
    private final String schema = "phase5_cart_" + UUID.randomUUID().toString().replace("-", "");
    private final HttpServer server;

    CartCheckoutBrokerFixture(Phase2LocalBrokerSupport stack) throws Exception {
        this.stack = stack;
        stack.execute("order", "create schema " + schema);
        stack.execute("order", "create table " + schema + ".cart (cart_id uuid primary key, customer_id uuid unique not null)");
        stack.execute("order", """
                create table %s.item (cart_item_id uuid primary key, cart_id uuid not null references %s.cart,
                    product_id uuid not null, quantity integer not null, row_version bigint not null)
                """.formatted(schema, schema));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/cart/checkout-items", this::checkoutItems);
        server.start();
    }

    URI origin() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    UUID cart(UUID customer) throws Exception {
        UUID cart = UUID.randomUUID();
        stack.execute("order", "insert into " + schema + ".cart values (?, ?)", cart, customer);
        return cart;
    }

    Item add(UUID cart, UUID product, int quantity) throws Exception {
        var item = new Item(UUID.randomUUID(), product, quantity, 0);
        stack.execute("order", "insert into " + schema + ".item values (?, ?, ?, ?, ?)",
                item.cartItemId(), cart, product, quantity, item.rowVersion());
        return item;
    }

    private Snapshot snapshot(UUID owner) throws SQLException {
        try (var connection = stack.connection("order"); var query = connection.prepareStatement("""
                select c.cart_id, c.customer_id, i.cart_item_id, i.product_id, i.quantity, i.row_version
                from %s.cart c left join %s.item i using (cart_id) where c.customer_id=?
                order by i.cart_item_id
                """.formatted(schema, schema))) {
            query.setObject(1, owner);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) return null;
                UUID cart = rows.getObject(1, UUID.class);
                UUID customer = rows.getObject(2, UUID.class);
                var items = new HashMap<UUID, Item>();
                do {
                    UUID id = rows.getObject(3, UUID.class);
                    if (id != null) {
                        items.put(id, new Item(id, rows.getObject(4, UUID.class), rows.getInt(5), rows.getLong(6)));
                    }
                } while (rows.next());
                return new Snapshot(cart, customer, items);
            }
        }
    }

    private void checkoutItems(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!List.of(TOKEN).equals(exchange.getRequestHeaders().get("X-Service-Token"))) {
                error(exchange, 401, "UNAUTHENTICATED");
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                error(exchange, 400, "VALIDATION_ERROR");
                return;
            }
            JsonNode body = JSON.readTree(exchange.getRequestBody());
            var requested = body.path("cartItemIds");
            if (!body.path("customerId").isString() || !requested.isArray() || requested.isEmpty() || requested.size() > 50) {
                error(exchange, 400, "VALIDATION_ERROR");
                return;
            }
            UUID owner = UUID.fromString(body.path("customerId").asString());
            var ids = new HashSet<UUID>();
            for (var id : requested) {
                if (!id.isString() || !ids.add(UUID.fromString(id.asString()))) {
                    error(exchange, 400, "VALIDATION_ERROR");
                    return;
                }
            }
            var snapshot = snapshot(owner);
            if (snapshot == null || !snapshot.items().keySet().containsAll(ids)) {
                error(exchange, 404, "NOT_FOUND");
                return;
            }
            var items = new ArrayList<Item>();
            ids.forEach(id -> items.add(snapshot.items().get(id)));
            respond(exchange, 200, Map.of("data", Map.of(
                    "cartId", snapshot.cartId(), "customerId", snapshot.customerId(), "items", items)));
        } catch (SQLException failure) {
            throw new IOException(failure);
        }
    }

    private static void error(HttpExchange exchange, int status, String code) throws IOException {
        respond(exchange, status, Map.of("error", Map.of("code", code, "requestId", UUID.randomUUID())));
    }

    private static void respond(HttpExchange exchange, int status, Map<String, ?> data) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(data);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override
    public void close() throws Exception {
        server.stop(0);
        stack.execute("order", "drop schema " + schema + " cascade");
    }

    record Item(UUID cartItemId, UUID productId, int quantity, long rowVersion) {}
    private record Snapshot(UUID cartId, UUID customerId, Map<UUID, Item> items) {}
}
