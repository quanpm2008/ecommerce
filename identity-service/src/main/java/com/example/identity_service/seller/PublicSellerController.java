package com.example.identity_service.seller;

import com.example.identity_service.common.openapi.ErrorCodes;
import com.example.identity_service.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.example.identity_service.common.error.ErrorCode.NOT_FOUND;

/**
 * Public shop pages (API §6.1, UC-B02), the same for guests, customers, sellers and admins. Admin data lives in the
 * admin API, never here.
 */
@Tag(name = "Sellers", description = "Public shop lookup (UC-B02): only sellers that may sell (G-06)")
@RestController
@RequestMapping(PublicSellerController.PATH)
@RequiredArgsConstructor
public class PublicSellerController {

	public static final String PATH = "/api/v1/identity/sellers";

	private final SellerDirectory directory;

	@Operation(summary = "Search sellers", description = "Case-insensitive substring of the shop name, ordered by "
			+ "name. Only sellers with an ACTIVE account, APPROVED and ACTIVE shop and the SELLER role are listed.")
	@Parameter(name = "page", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "1",
			minimum = "1"))
	@Parameter(name = "size", in = ParameterIn.QUERY, schema = @Schema(type = "integer", defaultValue = "20",
			minimum = "1", maximum = "100"))
	@GetMapping
	ApiResponse<List<SellerPublic>> search(@Parameter(description = "Part of the shop name") @RequestParam(
			required = false) String q, @Parameter(hidden = true) Pageable pageable) {
		return ApiResponse.page(directory.search(q, pageable));
	}

	@Operation(summary = "A seller's public page", description = "Name, description and logo only. A seller that "
			+ "may not sell is 404, like an unknown id.")
	@ErrorCodes(NOT_FOUND)
	@GetMapping("/{sellerId}")
	ApiResponse<SellerPublic> get(@PathVariable UUID sellerId) {
		return ApiResponse.of(directory.get(sellerId));
	}

}
