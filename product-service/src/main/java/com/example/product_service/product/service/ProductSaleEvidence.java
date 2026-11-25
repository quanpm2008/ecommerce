package com.example.product_service.product.service;

import com.example.product_service.product.enums.ProductStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProductSaleEvidence {
    private final JdbcClient database;

    ProductSaleEvidence(JdbcClient database) {
        this.database = database;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void record(UUID product, UUID seller, ProductStatus status) {
        database.sql("""
                insert into product_sale_evidence(product_id, seller_id, status)
                values (:product, :seller, :status)
                """).param("product", product).param("seller", seller).param("status", status.name()).update();
    }

    public Evidence read(Instant completedAt, List<UUID> productIds) {
        var items = database.sql("""
                select product_id, evidence.seller_id, evidence.status
                from (select distinct unnest(array[:ids]::uuid[]) as product_id) requested
                left join lateral (
                    select seller_id, status from product_sale_evidence e
                    where e.product_id = requested.product_id and effective_at <= :completedAt
                    order by effective_at desc, evidence_id desc limit 1
                ) evidence on true
                """).param("ids", productIds).param("completedAt", Timestamp.from(completedAt))
                .query((row, index) -> {
                    String status = row.getString("status");
                    return new Item(row.getObject("product_id", UUID.class), row.getObject("seller_id", UUID.class),
                            status == null ? "UNKNOWN" : "SELLING".equals(status) ? "ELIGIBLE" : "INELIGIBLE");
                }).list();
        return new Evidence(completedAt, items);
    }

    public record Evidence(Instant completedAt, List<Item> items) {}
    public record Item(UUID productId, UUID sellerId, String decision) {}
}
