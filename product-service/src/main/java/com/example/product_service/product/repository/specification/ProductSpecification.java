package com.example.product_service.product.repository.specification;

import com.example.product_service.product.entity.Product;
import com.example.product_service.product.enums.ProductStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProductSpecification {

    public static Specification<Product> filterPublic(
            String search,
            UUID categoryId,
            UUID sellerId,
            Long minPriceVnd,
            Long maxPriceVnd,
            List<UUID> allowedSellerIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // Chỉ hiển thị SELLING
            predicates.add(cb.equal(root.get("status"), ProductStatus.SELLING));

            if (allowedSellerIds != null) {
                if (allowedSellerIds.isEmpty()) {
                    return cb.disjunction();
                }
                predicates.add(root.get("sellerId").in(allowedSellerIds));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }
            if (sellerId != null) {
                predicates.add(cb.equal(root.get("sellerId"), sellerId));
            }
            if (search != null && !search.trim().isEmpty()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("productName")), pattern));
            }
            if (minPriceVnd != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("priceVnd"), minPriceVnd));
            }
            if (maxPriceVnd != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("priceVnd"), maxPriceVnd));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Product> filterSeller(
            UUID sellerId,
            ProductStatus status,
            String search,
            UUID categoryId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("sellerId"), sellerId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }
            if (search != null && !search.trim().isEmpty()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("productName")), pattern));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Product> filterAdmin(
            String search,
            UUID sellerId,
            UUID categoryId,
            ProductStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (sellerId != null) {
                predicates.add(cb.equal(root.get("sellerId"), sellerId));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }
            if (search != null && !search.trim().isEmpty()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("productName")), pattern));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
