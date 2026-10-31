package com.example.identity_service.seller;

import com.example.identity_service.common.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Public shop pages (API §6.1, UC-B02), the same for guests, customers, sellers and admins. Admin data lives in the
 * admin API, never here.
 */
@RestController
@RequestMapping(PublicSellerController.PATH)
@RequiredArgsConstructor
public class PublicSellerController {

	public static final String PATH = "/api/v1/identity/sellers";

	private final SellerDirectory directory;

	@GetMapping
	ApiResponse<List<SellerPublic>> search(@RequestParam(required = false) String q, Pageable pageable) {
		return ApiResponse.page(directory.search(q, pageable));
	}

	@GetMapping("/{sellerId}")
	ApiResponse<SellerPublic> get(@PathVariable UUID sellerId) {
		return ApiResponse.of(directory.get(sellerId));
	}

}
