package com.example.finance_service.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
public class FinanceCommissionController {
	private final byte[] orderCredential;

	public FinanceCommissionController(@Value("${finance.internal.tokens.order:}") String orderCredential) {
		this.orderCredential = orderCredential.getBytes(StandardCharsets.UTF_8);
	}

	@GetMapping("/internal/v1/finance/commission-rate")
	public ResponseEntity<Map<String, Object>> commissionRate(
			@RequestHeader(value = "X-Service-Token", required = false) String supplied) {
		if (orderCredential.length == 0 || supplied == null || supplied.isBlank()
				|| !MessageDigest.isEqual(orderCredential, supplied.getBytes(StandardCharsets.UTF_8))) {
			return ResponseEntity.status(401).body(Map.of("error",
					Map.of("code", "UNAUTHENTICATED", "message", "Service credential is required")));
		}
		return ResponseEntity.ok(Map.of("data", Map.of("commissionBps", 500)));
	}
}
