package com.example.product_service.product.service;

import com.example.product_service.client.identity.IdentityClientAdapter;
import com.example.product_service.client.order.OrderInventoryClientAdapter;
import com.example.product_service.product.enums.ProductStatus;
import com.example.product_service.product.repository.ProductRepository;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
class ProductInventoryProvision {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ProductRepository products;
    private final OrderInventoryClientAdapter order;
    private final IdentityClientAdapter identity;
    private final ProductSaleEvidence saleEvidence;

    ProductInventoryProvision(JdbcTemplate jdbc, PlatformTransactionManager manager, ProductRepository products,
                              OrderInventoryClientAdapter order, IdentityClientAdapter identity, ProductSaleEvidence saleEvidence) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.products = products;
        this.order = order;
        this.identity = identity;
        this.saleEvidence = saleEvidence;
    }

    UUID register(UUID seller, String key, String hash, int quantity, ProductStatus target, Supplier<UUID> create) {
        return transactions.execute(tx -> {
            jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))",
                    (org.springframework.jdbc.core.RowCallbackHandler) row -> {}, seller + ":" + key);
            var previous = jdbc.query("""
                    select product_id, request_hash from product_inventory_provision
                    where seller_id = ? and request_key = ?
                    """, (row, index) -> new Registered(row.getObject(1, UUID.class), row.getString(2)), seller, key);
            if (!previous.isEmpty()) {
                var recorded = previous.getFirst();
                if (!hash.equals(recorded.hash())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED");
                }
                return recorded.productId();
            }
            var id = create.get();
            jdbc.update("""
                    insert into product_inventory_provision
                        (product_id, seller_id, request_key, request_hash, quantity, target_status, next_attempt_at)
                    values (?, ?, ?, ?, ?, ?, statement_timestamp())
                    """, id, seller, key, hash, quantity, target.name());
            return id;
        });
    }

    boolean pending(UUID productId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (select 1 from product_inventory_provision
                where product_id = ? and not confirmed)
                """, Boolean.class, productId));
    }

    void deliver(UUID productId) {
        UUID lease = UUID.randomUUID();
        var work = jdbc.query("""
                update product_inventory_provision set lease_id = ?,
                    lease_until = statement_timestamp() + interval '15 seconds'
                where product_id = ? and next_attempt_at <= statement_timestamp()
                    and (lease_until is null or lease_until <= statement_timestamp())
                returning seller_id, quantity, target_status
                """, (row, index) -> new Work(row.getObject(1, UUID.class), row.getInt(2),
                ProductStatus.valueOf(row.getString(3))), lease, productId);
        if (work.isEmpty()) {
            return;
        }
        var task = work.getFirst();
        try {
            order.initInventory(productId, task.sellerId(), task.quantity(), productId.toString());
            boolean eligible = task.target() == ProductStatus.SELLING && identity.requireCurrentSellability(task.sellerId());
            transactions.executeWithoutResult(tx -> {
                int claimed = jdbc.update("""
                        update product_inventory_provision
                        set confirmed = true, next_attempt_at = null, lease_id = null, lease_until = null
                        where product_id = ? and lease_id = ?
                        """, productId, lease);
                if (claimed == 1 && eligible && products.publishProvisioned(productId, task.sellerId()) == 1) {
                    saleEvidence.record(productId, task.sellerId(), ProductStatus.SELLING);
                }
            });
        } catch (FeignException | ResponseStatusException failure) {
            boolean conflict = failure instanceof FeignException feign && feign.status() == 409;
            jdbc.update("""
                    update product_inventory_provision
                    set next_attempt_at = case when ? then null else statement_timestamp() + interval '5 seconds' end,
                        lease_id = null, lease_until = null where product_id = ? and lease_id = ?
                    """, conflict, productId, lease);
            log.warn("Inventory provision for {} {}", productId, conflict ? "requires review" : "will retry");
        }
    }

    @Scheduled(fixedDelayString = "${product.inventory.provision.poll-ms:1000}")
    void retryDue() {
        var candidates = jdbc.queryForList("""
                select product_id from product_inventory_provision
                where next_attempt_at <= statement_timestamp()
                    and (lease_until is null or lease_until <= statement_timestamp())
                order by next_attempt_at limit 10
                """, UUID.class);
        candidates.forEach(this::deliver);
    }

    private record Registered(UUID productId, String hash) {}
    private record Work(UUID sellerId, int quantity, ProductStatus target) {}
}
