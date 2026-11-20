package com.example.cart_service.cart.domain;

import com.example.cart_service.common.error.ApiException;
import com.example.cart_service.common.error.ErrorCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P-10 (parameters.md, confirmed 18-19/11/2026): each line holds 1–99 units, a cart holds at most 50 different
 * products, and adding a product already in the cart adds to its line without going over 99. These are business
 * rules, not configuration.
 */
public final class CartLimits {

	public static final int MAX_QUANTITY_PER_LINE = 99;

	public static final int MAX_LINES = 50;

	private CartLimits() {
	}

	/** A requested line quantity: below 1 is a validation error (400), above 99 is QUANTITY_LIMIT_EXCEEDED (422). */
	public static void requireLineQuantity(int quantity) {
		if (quantity < 1) {
			throw ApiException.forField(ErrorCode.VALIDATION_ERROR, "quantity", "MIN");
		}
		if (quantity > MAX_QUANTITY_PER_LINE) {
			throw perLineExceeded(null);
		}
	}

	public static ApiException perLineExceeded(Integer currentQuantity) {
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("field", "quantity");
		detail.put("issue", "MAX_PER_LINE");
		detail.put("maxQuantityPerLine", MAX_QUANTITY_PER_LINE);
		if (currentQuantity != null) {
			detail.put("currentQuantity", currentQuantity);
		}
		return new ApiException(ErrorCode.QUANTITY_LIMIT_EXCEEDED, List.of(detail));
	}

	public static ApiException linesExceeded() {
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("issue", "MAX_LINES");
		detail.put("maxLines", MAX_LINES);
		return new ApiException(ErrorCode.QUANTITY_LIMIT_EXCEEDED, List.of(detail));
	}

}
