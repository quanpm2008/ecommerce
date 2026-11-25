package com.example.product_service.product.service;

import com.example.product_service.product.enums.ProductStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"eureka.client.enabled=false", "product.internal.tokens.order=order-evidence-fixture"})
@AutoConfigureMockMvc
class ProductSaleEvidenceTests {
    private static final String PATH = "/internal/v1/products/sale-evidence";

    @Autowired ProductSaleEvidence evidence;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate database;
    @Autowired MockMvc mvc;

    @Test
    void returnsHistoricalOwnerAndStatusThroughTheInternalEndpoint() throws Exception {
        UUID product = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        var tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(state -> evidence.record(product, seller, ProductStatus.SELLING));
        Instant captured = clock();
        tx.executeWithoutResult(state -> evidence.record(product, seller, ProductStatus.REMOVED));
        String request = """
                {"completedAt":"%s","productIds":["%s"]}
                """;
        mvc.perform(post(PATH).header("X-Service-Token", "order-evidence-fixture").contentType("application/json")
                        .content(request.formatted(captured, product)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].decision").value("ELIGIBLE"))
                .andExpect(jsonPath("$.data.items[0].sellerId").value(seller.toString()));
        mvc.perform(post(PATH).header("X-Service-Token", "order-evidence-fixture").contentType("application/json")
                        .content(request.formatted(clock(), product)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].decision").value("INELIGIBLE"));
        mvc.perform(post(PATH).header("X-Service-Token", "order-evidence-fixture").contentType("application/json")
                        .content(request.formatted(Instant.EPOCH, product)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].decision").value("UNKNOWN"));
        mvc.perform(post(PATH).header("X-Service-Token", "not-order").contentType("application/json")
                        .content(request.formatted(captured, product))).andExpect(status().isUnauthorized());
    }

    private Instant clock() {
        return database.queryForObject("select clock_timestamp()", Timestamp.class).toInstant();
    }
}
