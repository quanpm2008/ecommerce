package com.example.order_service.exception;

import java.util.Map;

public final class SellerInventoryRejected extends RuntimeException {
	private final int status;
	private final String code;
	private final Map<String, ?> detail;

	public SellerInventoryRejected(int status, String code, Map<String, ?> detail) {
		this.status = status;
		this.code = code;
		this.detail = detail;
	}

	public int status() {
		return status;
	}

	public String code() {
		return code;
	}

	public Map<String, ?> detail() {
		return detail;
	}
}
