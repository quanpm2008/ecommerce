package com.example.order_service.service;

import com.example.order_service.client.PayIdentityClient.ShippingAddress;
import com.example.order_service.client.PaymentCheckoutClient;
import com.example.order_service.client.PaymentCheckoutClient.Refund;
import com.example.order_service.client.ProductSaleEvidenceClient;
import com.example.order_service.client.ProductSaleEvidenceClient.Review;
import com.example.order_service.client.ProductSaleEvidenceClient.ReviewRequest;
import com.example.order_service.dto.response.CheckoutResponse.FirstItem;
import com.example.order_service.dto.response.CheckoutResponse.Seller;
import com.example.order_service.entity.Order;
import com.example.order_service.entity.Order.Status;
import com.example.order_service.entity.OrderItem;
import com.example.order_service.repository.CheckoutOrderRepository;
import com.example.order_service.repository.InventoryStockRepository;
import com.example.order_service.repository.OrderItemRepository;
import com.example.order_service.security.OrderAuthorization.CurrentUser;
import com.example.order_service.service.OrderLifecycleService.Rejected;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderLifecycleDetail {
	private final CheckoutOrderRepository orders;
	private final OrderItemRepository items;
	private final InventoryStockRepository clock;
	private final PaymentCheckoutClient payment;
	private final ProductSaleEvidenceClient product;
	private final String paymentToken;
	private final String productToken;

	public OrderLifecycleDetail(CheckoutOrderRepository orders, OrderItemRepository items, InventoryStockRepository clock,
			PaymentCheckoutClient payment, ProductSaleEvidenceClient product,
			@Value("${order.payment.service-token:}") String paymentToken,
			@Value("${order.product.service-token:}") String productToken) {
		this.orders = orders;
		this.items = items;
		this.clock = clock;
		this.payment = payment;
		this.product = product;
		this.paymentToken = paymentToken;
		this.productToken = productToken;
	}

	public Map<String, Object> customer(UUID orderId, CurrentUser user, boolean actionResponse) {
		Order order = owned(orderId, user.userId(), false);
		var lines = lines(order);
		Refund refund = order.getStatus() == Status.CANCELLED ? readRefund(orderId, user) : null;
		boolean refundPending = order.getStatus() == Status.CANCELLED && order.getPaidAt() != null && refund == null;
		boolean open = order.getStatus() == Status.PAID || order.getStatus() == Status.PACKING
				|| order.getStatus() == Status.DELIVERED || order.getStatus() == Status.AWAITING_PAYMENT;
		if (user.limited() && !actionResponse && !open && !refundPending && (refund == null || "SUCCESS".equals(refund.status()))) {
			throw new Rejected(403, "ACCOUNT_BANNED", null);
		}
		var actions = new ArrayList<String>();
		if (!user.readOnly() && order.getStatus() == Status.PAID && user.permissions().contains("order:cancel:own")) {
			actions.add("CANCEL");
		}
		if (!user.readOnly() && order.getStatus() == Status.DELIVERED && user.permissions().contains("order:confirm:own")) {
			actions.add("CONFIRM_RECEIPT");
		}
		var reviews = order.getStatus() == Status.CONFIRMED && !user.limited() ? reviews(order, lines, user) : List.<ReviewSummary>of();
		if (reviews.stream().anyMatch(ReviewSummary::canReview)) {
			actions.add("REVIEW");
		}
		var response = base(order, lines, actions);
		response.put("seller", new Seller(order.getSellerId(), order.getSellerNameSnapshot()));
		response.put("itemCount", lines.size());
		var first = lines.getFirst();
		response.put("firstItem", new FirstItem(first.getProductNameSnapshot(), first.getCoverMediaSnapshot()));
		response.put("createdAt", order.getCreatedAt());
		response.put("refundStatus", refund == null ? null : refund.status());
		response.put("refund", refund);
		response.put("refundPendingCreation", refundPending);
		response.put("reviewSummary", reviews);
		return response;
	}

	public Map<String, Object> seller(UUID orderId, CurrentUser user, boolean actionResponse) {
		Order order = owned(orderId, user.userId(), true);
		boolean workOpen = order.getStatus() == Status.PAID || order.getStatus() == Status.PACKING;
		if (user.limited() && !actionResponse && !workOpen && order.getStatus() != Status.DELIVERED) {
			throw new Rejected(403, "ACCOUNT_BANNED", null);
		}
		return sellerDetails(order, workOpen, true);
	}

	public Map<String, Object> admin(Order order) {
		return sellerDetails(order, false, false);
	}

	private Map<String, Object> sellerDetails(Order order, boolean workOpen, boolean sellerActor) {
		List<String> actions = sellerActor ? sellerActions(order, workOpen) : List.of();
		var response = base(order, lines(order), actions);
		long gross = order.getTotalAmountVnd();
		int bps = order.getCommissionBpsSnapshot();
		long commission = (gross / 10_000) * bps + ((gross % 10_000) * bps + 5_000) / 10_000;
		response.put("commissionBps", bps);
		response.put("commissionAmountVnd", commission);
		response.put("sellerAmountVnd", gross - commission);
		return response;
	}

	List<String> sellerActions(Order order) {
		return sellerActions(order, order.getStatus() == Status.PAID || order.getStatus() == Status.PACKING);
	}

	private List<String> sellerActions(Order order, boolean workOpen) {
		var actions = new ArrayList<String>();
		if (workOpen && order.getSellerActionDueAt() != null && clock.databaseNow().isBefore(order.getSellerActionDueAt())) {
			actions.add(order.getStatus() == Status.PAID ? "START_PACKING" : "MARK_DELIVERED");
			actions.add("REJECT");
		}
		return actions;
	}

	private Map<String, Object> base(Order order, List<OrderItem> lines, List<String> actions) {
		var response = new LinkedHashMap<String, Object>();
		response.put("orderId", order.getOrderId());
		response.put("checkoutSessionId", order.getCheckoutSessionId());
		response.put("status", order.getStatus());
		response.put("totalAmountVnd", order.getTotalAmountVnd());
		response.put("shippingFeeVnd", 0);
		response.put("items", lines.stream().map(Item::from).toList());
		response.put("shippingAddress", new ShippingAddress(order.getRecipientName(), order.getRecipientPhone(),
				order.getProvinceCode(), order.getProvinceName(), order.getWardCode(), order.getWardName(), order.getAddressDetail()));
		response.put("paidAt", order.getPaidAt());
		response.put("sellerActionDueAt", order.getSellerActionDueAt());
		response.put("deliveredAt", order.getDeliveredAt());
		response.put("customerConfirmDueAt", order.getCustomerConfirmDueAt());
		response.put("confirmedAt", order.getConfirmedAt());
		response.put("cancellation", order.getCancelledAt() == null ? null : new Cancellation(order.getCancelledByType(),
				order.getCancelReasonCode(), order.getCancelReasonNote(), order.getCancelledAt()));
		response.put("timeline", timeline(order));
		response.put("allowedActions", actions);
		response.put("isSimulated", true);
		return response;
	}

	private static List<Timeline> timeline(Order order) {
		var timeline = new ArrayList<Timeline>();
		add(timeline, Status.AWAITING_PAYMENT, order.getCreatedAt(), "CUSTOMER");
		add(timeline, Status.PAID, order.getPaidAt(), "SYSTEM");
		add(timeline, Status.PACKING, order.getPackingAt(), "SELLER");
		add(timeline, Status.DELIVERED, order.getDeliveredAt(), "SELLER");
		add(timeline, Status.CONFIRMED, order.getConfirmedAt(), order.getConfirmedBy());
		add(timeline, Status.CANCELLED, order.getCancelledAt(), order.getCancelledByType());
		if (order.getStatus() == Status.EXPIRED || order.getStatus() == Status.PAYMENT_FAILED) {
			add(timeline, order.getStatus(), order.getUpdatedAt(), "SYSTEM");
		}
		return timeline;
	}

	private static void add(List<Timeline> timeline, Status status, Instant at, Object actor) {
		if (at != null) {
			timeline.add(new Timeline(status, at, actor.toString()));
		}
	}

	private Refund readRefund(UUID orderId, CurrentUser user) {
		try {
			var reply = payment.refunds(orderId, paymentToken);
			if (reply == null || reply.data() == null || reply.data().size() > 1) {
				throw unavailable();
			}
			if (reply.data().isEmpty()) {
				return null;
			}
			var refund = reply.data().getFirst();
			if (refund == null || !orderId.equals(refund.orderId()) || !"ORDER".equals(refund.scope())) {
				throw unavailable();
			}
			return refund.withRetryPermission(!user.readOnly() && user.permissions().contains("refund:retry:own"));
		} catch (FeignException upstream) {
			throw unavailable();
		}
	}

	private List<ReviewSummary> reviews(Order order, List<OrderItem> lines, CurrentUser user) {
		try {
			var products = lines.stream().map(OrderItem::getProductId).distinct().toList();
			var reply = product.reviews(productToken, new ReviewRequest(order.getCustomerId(), products));
			if (reply == null || reply.data() == null || reply.data().size() != products.size()) {
				throw unavailable();
			}
			var indexed = new LinkedHashMap<UUID, Review>();
			for (var review : reply.data()) {
				if (review == null || review.productId() == null || indexed.putIfAbsent(review.productId(), review) != null) {
					throw unavailable();
				}
			}
			return lines.stream().map(line -> {
				var review = indexed.get(line.getProductId());
				if (review == null || review.hasReviewed() == null || review.hasReviewed() != (review.reviewId() != null)) {
					throw unavailable();
				}
				return new ReviewSummary(line.getOrderItemId(), line.getProductId(), review.hasReviewed(), review.reviewId(),
						!review.hasReviewed() && user.permissions().contains("review:write:own"));
			}).toList();
		} catch (FeignException upstream) {
			throw unavailable();
		}
	}

	private List<OrderItem> lines(Order order) {
		var lines = items.findByOrderIdInOrderByProductId(List.of(order.getOrderId()));
		if (lines.isEmpty()) {
			throw unavailable();
		}
		return lines;
	}

	private Order owned(UUID id, UUID user, boolean seller) {
		var order = orders.findById(id).orElse(null);
		if (order == null || !user.equals(seller ? order.getSellerId() : order.getCustomerId())
				|| seller && order.getPaidAt() == null) {
			throw new Rejected(404, "NOT_FOUND", null);
		}
		return order;
	}

	private static Rejected unavailable() {
		return new Rejected(503, "SERVICE_UNAVAILABLE", null);
	}

	private record Item(UUID orderItemId, UUID productId, String productName, String coverImageUrl, long unitPriceVnd,
			int quantity, long lineTotalVnd) {
		static Item from(OrderItem row) {
			return new Item(row.getOrderItemId(), row.getProductId(), row.getProductNameSnapshot(), row.getCoverMediaSnapshot(),
					row.getUnitPriceVndSnapshot(), row.getQuantity(), row.getLineTotalVnd());
		}
	}

	private record Cancellation(Order.CancelledByType cancelledByType, String reasonCode, String reasonNote, Instant cancelledAt) {}
	private record ReviewSummary(UUID orderItemId, UUID productId, boolean hasReviewed, UUID reviewId, boolean canReview) {}
	private record Timeline(Status status, Instant at, String actor) {}
}
