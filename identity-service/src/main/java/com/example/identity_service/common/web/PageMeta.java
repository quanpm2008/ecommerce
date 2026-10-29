package com.example.identity_service.common.web;

import org.springframework.data.domain.Page;

/**
 * Paging metadata with a 1-based page number (API §3.5).
 */
public record PageMeta(int page, int size, long totalItems, int totalPages) {

	public static PageMeta from(Page<?> page) {
		return new PageMeta(page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
	}

}
