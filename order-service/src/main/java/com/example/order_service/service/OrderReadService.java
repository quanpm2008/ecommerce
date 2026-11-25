package com.example.order_service.service;

import com.example.order_service.client.OrderAdminIdentityClient;
import com.example.order_service.client.PaymentCheckoutClient;
import com.example.order_service.dto.response.CheckoutResponse.FirstItem;
import com.example.order_service.dto.response.CheckoutResponse.Seller;
import com.example.order_service.entity.Order;
import com.example.order_service.entity.Order.Status;
import com.example.order_service.entity.OrderItem;
import com.example.order_service.repository.CheckoutOrderRepository;
import com.example.order_service.repository.CheckoutSessionJpaRepository;
import com.example.order_service.repository.OrderItemRepository;
import com.example.order_service.security.OrderAuthorization.CurrentUser;
import com.example.order_service.service.OrderLifecycleService.Rejected;
import feign.FeignException;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class OrderReadService {
	private static final List<Status> SELLER_OPEN = List.of(Status.PAID, Status.PACKING, Status.DELIVERED);
	private final CheckoutOrderRepository orders;
	private final CheckoutSessionJpaRepository sessions;
	private final OrderItemRepository items;
	private final OrderLifecycleDetail details;
	private final PaymentCheckoutClient payment;
	private final OrderAdminIdentityClient identity;
	private final String paymentToken;

	public OrderReadService(CheckoutOrderRepository orders, CheckoutSessionJpaRepository sessions, OrderItemRepository items,
			OrderLifecycleDetail details, PaymentCheckoutClient payment, OrderAdminIdentityClient identity,
			@Value("${order.payment.service-token:}") String paymentToken) {
		this.orders = orders;
		this.sessions = sessions;
		this.items = items;
		this.details = details;
		this.payment = payment;
		this.identity = identity;
		this.paymentToken = paymentToken;
	}

	public Map<String, Object> search(View view, CurrentUser actor, Filters filters, String bearer) {
		var pageRequest = page(view, filters);
		var scope = specification(view, actor, filters);
		var page = view == View.CUSTOMER && actor.limited()
				? limitedCustomerPage(scope, pageRequest) : orders.findAll(scope, pageRequest);
		var lines = page.isEmpty() ? Map.<UUID, List<OrderItem>>of() :
				items.findByOrderIdInOrderByProductId(page.getContent().stream().map(Order::getOrderId).toList()).stream()
						.collect(Collectors.groupingBy(OrderItem::getOrderId));
		var refunds = view == View.SELLER ? Map.<UUID, PaymentCheckoutClient.Refund>of() : refunds(page.getContent());
		var names = view == View.ADMIN ? page.stream().map(Order::getCustomerId).distinct()
				.collect(Collectors.toMap(Function.identity(), id -> customer(id, bearer).fullName())) : Map.<UUID, String>of();
		var data = page.stream().map(order -> summary(view, order, lines.getOrDefault(order.getOrderId(), List.of()),
				refunds.get(order.getOrderId()), names.get(order.getCustomerId()))).toList();
		return Map.of("data", data, "meta", Map.of("page", filters.page(), "size", filters.size(),
				"totalItems", page.getTotalElements(), "totalPages", page.getTotalPages()));
	}

	public Map<String, Object> adminDetail(UUID id, String bearer) {
		Order order = orders.findById(id).orElseThrow(() -> new Rejected(404, "NOT_FOUND", null));
		var session = sessions.findById(order.getCheckoutSessionId()).orElseThrow(OrderReadService::unavailable);
		var customer = customer(order.getCustomerId(), bearer);
		var data = details.admin(order);
		data.put("customer", new Customer(customer.userId(), customer.fullName(), customer.email()));
		String status = session.getCloseReason() == null ? session.getStatus().name() : session.getCloseReason().name();
		data.put("checkoutSession", new Checkout(session.getCheckoutSessionId(), status,
				session.getExpiresAt(), session.getPaidAt()));
		return Map.of("data", data);
	}

	private Page<Order> limitedCustomerPage(Specification<Order> scope, PageRequest request) {
		List<Order> candidates = orders.findAll(scope, request.getSort());
		var refunds = refunds(candidates);
		var allowed = candidates.stream().filter(order -> switch (order.getStatus()) {
			case PAID, PACKING, DELIVERED, AWAITING_PAYMENT -> true;
			case CANCELLED -> order.getPaidAt() != null && (refunds.get(order.getOrderId()) == null
					|| !"SUCCESS".equals(refunds.get(order.getOrderId()).status()));
			default -> false;
		}).toList();
		return new PageImpl<>(allowed.stream().skip(request.getOffset()).limit(request.getPageSize()).toList(),
				request, allowed.size());
	}

	private Specification<Order> specification(View view, CurrentUser actor, Filters filter) {
		String q = filter.q() == null ? "" : filter.q().strip();
		UUID lookup = uuid(q);
		Instant overdueBefore = Instant.now();
		return (root, query, cb) -> {
			var where = new ArrayList<Predicate>();
			switch (view) {
				case CUSTOMER -> {
					where.add(cb.equal(root.get("customerId"), actor.userId()));
					if (actor.limited()) {
						where.add(root.get("status").in(Status.PAID, Status.PACKING, Status.DELIVERED,
								Status.AWAITING_PAYMENT, Status.CANCELLED));
					}
					if (!q.isEmpty()) {
						var name = cb.like(cb.lower(root.get("sellerNameSnapshot")),
								"%" + q.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%", '\\');
						where.add(lookup == null ? name : cb.or(name, cb.equal(root.get("orderId"), lookup),
								cb.equal(root.get("checkoutSessionId"), lookup)));
					}
				}
				case SELLER -> {
					where.add(cb.equal(root.get("sellerId"), actor.userId()));
					where.add(cb.isNotNull(root.get("paidAt")));
					if (actor.limited()) {
						where.add(root.get("status").in(SELLER_OPEN));
					}
					if (!q.isEmpty()) {
						where.add(lookup == null ? cb.disjunction() : cb.equal(root.get("orderId"), lookup));
					}
					if (filter.dueBefore() != null) {
						where.add(cb.lessThan(root.get("sellerActionDueAt"), filter.dueBefore()));
					}
				}
				case ADMIN -> {
					if (filter.orderId() != null) where.add(cb.equal(root.get("orderId"), filter.orderId()));
					if (filter.customerId() != null) where.add(cb.equal(root.get("customerId"), filter.customerId()));
					if (filter.sellerId() != null) where.add(cb.equal(root.get("sellerId"), filter.sellerId()));
					if ("SELLER".equals(filter.overdue())) {
						where.add(root.get("status").in(Status.PAID, Status.PACKING));
						where.add(cb.lessThanOrEqualTo(root.get("sellerActionDueAt"), overdueBefore));
					}
					if ("CUSTOMER".equals(filter.overdue())) {
						where.add(cb.equal(root.get("status"), Status.DELIVERED));
						where.add(cb.lessThanOrEqualTo(root.get("customerConfirmDueAt"), overdueBefore));
					}
				}
			}
			if (filter.checkoutSessionId() != null && view != View.SELLER) {
				where.add(cb.equal(root.get("checkoutSessionId"), filter.checkoutSessionId()));
			}
			if (filter.status() != null && !filter.status().isEmpty()) where.add(root.get("status").in(filter.status()));
			return cb.and(where.toArray(Predicate[]::new));
		};
	}

	private Map<String, Object> summary(View view, Order order, List<OrderItem> items, PaymentCheckoutClient.Refund refund,
			String customerName) {
		var row = new LinkedHashMap<String, Object>();
		row.put("orderId", order.getOrderId());
		row.put("checkoutSessionId", order.getCheckoutSessionId());
		row.put("status", order.getStatus());
		row.put("totalAmountVnd", order.getTotalAmountVnd());
		row.put("paidAt", order.getPaidAt());
		if (view != View.SELLER) {
			row.put("seller", new Seller(order.getSellerId(), order.getSellerNameSnapshot()));
			row.put("createdAt", order.getCreatedAt());
			row.put("itemCount", items.size());
			row.put("firstItem", items.isEmpty() ? null :
					new FirstItem(items.getFirst().getProductNameSnapshot(), items.getFirst().getCoverMediaSnapshot()));
			row.put("refundStatus", refund == null ? null : refund.status());
		}
		if (view != View.CUSTOMER) {
			row.put("sellerActionDueAt", order.getSellerActionDueAt());
			row.put("customerConfirmDueAt", order.getCustomerConfirmDueAt());
		}
		if (view == View.SELLER) {
			row.put("items", items.stream().map(item -> new SellerItem(item.getProductNameSnapshot(), item.getQuantity())).toList());
			row.put("allowedActions", details.sellerActions(order));
		}
		if (view == View.ADMIN) {
			row.put("customerId", order.getCustomerId());
			row.put("customerName", customerName);
		}
		return row;
	}

	private Map<UUID, PaymentCheckoutClient.Refund> refunds(List<Order> candidates) {
		var ids = candidates.stream().filter(order -> order.getStatus() == Status.CANCELLED && order.getPaidAt() != null)
				.map(Order::getOrderId).toList();
		var result = new LinkedHashMap<UUID, PaymentCheckoutClient.Refund>();
		for (int start = 0; start < ids.size(); start += 100) {
			var batch = ids.subList(start, Math.min(start + 100, ids.size()));
			try {
				var response = payment.refundsByOrders(batch, paymentToken);
				if (response == null || response.data() == null) throw unavailable();
				for (var refund : response.data()) {
					if (!"ORDER".equals(refund.scope()) || !batch.contains(refund.orderId())
							|| result.putIfAbsent(refund.orderId(), refund) != null) throw unavailable();
				}
			} catch (FeignException failure) {
				throw unavailable();
			}
		}
		return result;
	}

	private OrderAdminIdentityClient.User customer(UUID id, String bearer) {
		try {
			var result = identity.customer(id, bearer);
			if (result == null || result.data() == null || result.data().user() == null
					|| !id.equals(result.data().user().userId()) || result.data().user().fullName() == null) {
				throw unavailable();
			}
			return result.data().user();
		} catch (FeignException failure) {
			throw unavailable();
		}
	}

	private static PageRequest page(View view, Filters f) {
		if (f.page() < 1 || f.size() < 1 || f.size() > 100
				|| (f.overdue() != null && !List.of("SELLER", "CUSTOMER").contains(f.overdue()))) {
			throw new Rejected(400, "VALIDATION_ERROR", null);
		}
		Sort sort = switch (f.sort() == null ? "" : f.sort()) {
			case "" -> Sort.by(Sort.Direction.DESC, view == View.SELLER ? "paidAt" : "createdAt");
			case "paidAt,desc", "paidAt" -> Sort.by(Sort.Direction.DESC, "paidAt");
			case "sellerActionDueAt,asc" -> Sort.by("sellerActionDueAt");
			case "customerConfirmDueAt,asc" -> Sort.by("customerConfirmDueAt");
			case "createdAt,asc" -> Sort.by("createdAt");
			case "createdAt,desc" -> Sort.by(Sort.Direction.DESC, "createdAt");
			default -> throw new Rejected(400, "VALIDATION_ERROR", null);
		};
		return PageRequest.of(f.page() - 1, f.size(), sort.and(Sort.by("orderId")));
	}

	private static UUID uuid(String q) {
		try {
			return q.length() == 36 ? UUID.fromString(q) : null;
		} catch (IllegalArgumentException badId) {
			return null;
		}
	}

	private static Rejected unavailable() {
		return new Rejected(503, "SERVICE_UNAVAILABLE", null);
	}

	public enum View { CUSTOMER, SELLER, ADMIN }
	public record Filters(List<Status> status, UUID checkoutSessionId, UUID orderId, UUID sellerId, UUID customerId,
			String q, Instant dueBefore, String overdue, String sort, int page, int size) {}
	private record Customer(UUID customerId, String fullName, String email) {}
	private record Checkout(UUID checkoutSessionId, String status, Instant expiresAt, Instant paidAt) {}
	private record SellerItem(String productName, int quantity) {}
}
