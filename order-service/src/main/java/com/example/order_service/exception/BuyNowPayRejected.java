package com.example.order_service.exception;

public final class BuyNowPayRejected extends RuntimeException {
	private final int status;
	private final String code;

	public BuyNowPayRejected(int status, String code) {
		this.status = status;
		this.code = code;
	}

	public int status() {
		return status;
	}

	public String code() {
		return code;
	}
}
