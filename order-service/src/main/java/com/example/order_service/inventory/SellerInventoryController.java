package com.example.order_service.inventory;

import com.example.order_service.client.ProductBatchClient.ProductBatchUnavailable;
import com.example.order_service.security.OrderAuthorization.CurrentUser;
import com.example.order_service.web.OrderApiExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders/seller/inventory")
class SellerInventoryController {
	private final SellerInventoryService inventory;
	private final OrderApiExceptionHandler errors;

	SellerInventoryController(SellerInventoryService inventory, OrderApiExceptionHandler errors) {
		this.inventory = inventory;
		this.errors = errors;
	}

	@GetMapping
	InventoryReadController.Data<List<SellerInventoryService.Item>> list(@AuthenticationPrincipal CurrentUser seller,
			@RequestParam(required = false) List<UUID> productIds, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int size) {
		validatePage(page, size);
		if (productIds != null && (productIds.isEmpty() || productIds.size() > 100 || productIds.contains(null))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		}
		return new InventoryReadController.Data<>(inventory.list(seller.userId(), productIds, page, size));
	}

	@PutMapping("/{productId}")
	InventoryReadController.Data<SellerInventoryService.Item> update(@AuthenticationPrincipal CurrentUser seller,
			@PathVariable UUID productId, @RequestBody Adjustment body) {
		if (body.onHandQuantity() == null || body.onHandQuantity() < 0 || body.version() == null || body.version() < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		}
		return new InventoryReadController.Data<>(inventory.update(seller.userId(), productId, body.version(),
				body.onHandQuantity(), body.note()));
	}

	@GetMapping("/{productId}/movements")
	SellerInventoryService.MovementPage movements(@AuthenticationPrincipal CurrentUser seller,
			@PathVariable UUID productId, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int size) {
		validatePage(page, size);
		return inventory.listMovements(seller.userId(), productId, page, size);
	}

	@ExceptionHandler(SellerInventoryService.Rejected.class)
	void rejected(SellerInventoryService.Rejected failure, HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		String message = switch (failure.code) {
			case "STOCK_BELOW_RESERVED" -> "Tồn kho mới thấp hơn số lượng đang giữ";
			case "VERSION_CONFLICT" -> "Phiên bản tồn kho đã thay đổi";
			case "NOT_FOUND" -> "Không tìm thấy tồn kho";
			default -> "Không thể xác nhận dữ liệu sản phẩm";
		};
		errors.write(request, response, failure.status, failure.code, message,
				failure.detail.isEmpty() ? List.of() : List.of(failure.detail));
	}

	@ExceptionHandler(ProductBatchUnavailable.class)
	void productUnavailable(HttpServletRequest request, HttpServletResponse response) throws IOException {
		errors.write(request, response, 503, "SERVICE_UNAVAILABLE", "Không thể xác nhận dữ liệu sản phẩm");
	}

	private static void validatePage(int page, int size) {
		if (page < 1 || size < 1 || size > 100) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		}
	}

	record Adjustment(Integer onHandQuantity, Long version, String note) {
	}
}
