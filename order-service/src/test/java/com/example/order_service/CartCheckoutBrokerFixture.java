package com.example.order_service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import tools.jackson.core.JacksonException;
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
import java.util.Set;
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
        stack.execute("order", """
                create table %s.inbox (event_id uuid primary key, checkout_session_id uuid unique not null)
                """.formatted(schema));
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

    void changeQuantity(Item item, int quantity) throws Exception {
        stack.execute("order", "update " + schema + ".item set quantity=?, row_version=row_version+1 where cart_item_id=?",
                quantity, item.cartItemId());
    }

    void remove(Item item) throws Exception {
        stack.execute("order", "delete from " + schema + ".item where cart_item_id=?", item.cartItemId());
    }

    Map<UUID, Item> items(UUID owner) throws SQLException {
        return Map.copyOf(snapshot(owner).items());
    }

    int appliedCount(UUID checkout) throws Exception {
        return Integer.parseInt(stack.db("order", "select count(*) from " + schema + ".inbox where checkout_session_id=?", checkout));
    }

    boolean apply(ConsumerRecord<String, String> record) throws SQLException {
        return apply(record, () -> {});
    }

    boolean apply(ConsumerRecord<String, String> record, Runnable beforeCommit) throws SQLException {
        var envelope = JSON.readTree(record.value());
        var payload = envelope.path("payload");
        if (!"checkout.paid".equals(envelope.path("eventType").asString())
                || !"CART".equals(payload.path("source").asString())) {
            return false;
        }
        UUID checkout = UUID.fromString(payload.path("checkoutSessionId").asString());
        if (!checkout.toString().equals(record.key())) {
            throw new IllegalArgumentException("Cart checkout event key differs from its checkoutSessionId");
        }
        UUID customer = UUID.fromString(payload.path("customerId").asString());
        UUID event = UUID.fromString(envelope.path("eventId").asString());
        try (var connection = stack.connection("order")) {
            connection.setAutoCommit(false);
            try (var insert = connection.prepareStatement("insert into " + schema + ".inbox values (?, ?) on conflict do nothing");
                    var update = connection.prepareStatement("""
                        update %s.item set quantity=greatest(quantity-?,0), row_version=row_version+1
                        where cart_item_id=? and cart_id=(select cart_id from %s.cart where customer_id=?)
                        """.formatted(schema, schema));
                    var delete = connection.prepareStatement("""
                        delete from %s.item where quantity=0 and cart_item_id=?
                        and cart_id=(select cart_id from %s.cart where customer_id=?)
                        """.formatted(schema, schema))) {
                insert.setObject(1, event);
                insert.setObject(2, checkout);
                if (insert.executeUpdate() == 0) {
                    connection.rollback();
                    return false;
                }
                for (var item : payload.path("cartItems")) {
                    UUID id = UUID.fromString(item.path("cartItemId").asString());
                    update.setInt(1, item.path("quantity").asInt());
                    update.setObject(2, id);
                    update.setObject(3, customer);
                    update.executeUpdate();
                    delete.setObject(1, id);
                    delete.setObject(2, customer);
                    delete.executeUpdate();
                }
                beforeCommit.run();
                connection.commit();
                return true;
            } catch (RuntimeException | SQLException failure) {
                connection.rollback();
                throw failure;
            }
        }
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
            Selection selection;
            try {
                selection = selection(JSON.readTree(exchange.getRequestBody()));
            } catch (JacksonException | IllegalArgumentException invalid) {
                error(exchange, 400, "VALIDATION_ERROR");
                return;
            }
            var snapshot = snapshot(selection.owner());
            if (snapshot == null || !snapshot.items().keySet().containsAll(selection.ids())) {
                error(exchange, 404, "NOT_FOUND");
                return;
            }
            var items = new ArrayList<Item>();
            selection.ids().forEach(id -> items.add(snapshot.items().get(id)));
            respond(exchange, 200, Map.of("data", Map.of(
                    "cartId", snapshot.cartId(), "customerId", snapshot.customerId(), "items", items)));
        } catch (SQLException failure) {
            throw new IOException(failure);
        }
    }

    private static Selection selection(JsonNode body) {
        if (body == null || !body.path("customerId").isString()) {
            throw new IllegalArgumentException("Customer UUID required");
        }
        var requested = body.path("cartItemIds");
        if (!requested.isArray() || requested.isEmpty() || requested.size() > 50) {
            throw new IllegalArgumentException("Specify 1–50 Cart line IDs");
        }
        var ids = new HashSet<UUID>();
        for (var id : requested) {
            if (!id.isString() || !ids.add(UUID.fromString(id.asString()))) {
                throw new IllegalArgumentException("Cart line UUIDs must be distinct");
            }
        }
        return new Selection(UUID.fromString(body.path("customerId").asString()), ids);
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
    private record Selection(UUID owner, Set<UUID> ids) {}
    private record Snapshot(UUID cartId, UUID customerId, Map<UUID, Item> items) {}
}
