package com.example.identity_service.internal;

import com.example.identity_service.common.web.ApiResponse;
import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
class SellerSaleEvidenceController {
	private final JdbcClient database;

	SellerSaleEvidenceController(JdbcClient database) {
		this.database = database;
	}

	@PostMapping("/internal/v1/identity/sellers/sale-evidence")
	@PreAuthorize("hasAuthority('service:order-service')")
	ApiResponse<Evidence> read(@Valid @RequestBody Query query) {
		try {
			var items = database.sql("""
					select requested.seller_id, evidence.eligible
					from (select distinct unnest(array[:ids]::uuid[]) as seller_id) requested
					left join lateral (
						select eligible from identity.seller_sale_evidence e
						where e.seller_id = requested.seller_id and effective_at <= :completedAt
						order by effective_at desc, evidence_id desc limit 1
					) evidence on true
					""").param("ids", query.sellerIds()).param("completedAt", Timestamp.from(query.completedAt()))
					.query((row, index) -> {
						Boolean eligible = row.getObject("eligible", Boolean.class);
						return new Item(row.getObject("seller_id", UUID.class),
								eligible == null ? "UNKNOWN" : eligible ? "ELIGIBLE" : "INELIGIBLE");
					}).list();
			return ApiResponse.of(new Evidence(query.completedAt(), items));
		} catch (DataAccessException unavailable) {
			throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
		}
	}

	record Query(@NotNull Instant completedAt, @NotNull @Size(min = 1) List<@NotNull UUID> sellerIds) {}
	record Evidence(Instant completedAt, List<Item> items) {}
	record Item(UUID sellerId, String decision) {}
}
