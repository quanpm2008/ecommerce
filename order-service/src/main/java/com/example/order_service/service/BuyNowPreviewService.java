package com.example.order_service.service;

import com.example.order_service.client.CartCheckoutItemsClient;
import com.example.order_service.client.CartCheckoutItemsClient.Line;
import com.example.order_service.client.CheckoutIdentityClient.Address;
import com.example.order_service.client.CheckoutIdentityClient.Seller;
import com.example.order_service.client.CheckoutIdentityClient;
import com.example.order_service.client.ProductBatchClient;
import com.example.order_service.repository.InventoryStockRepository;
import com.example.order_service.repository.PendingCheckoutReader;
import com.example.order_service.security.OrderAuthorization.CurrentUser;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BuyNowPreviewService {
	private final CheckoutIdentityClient identity;
	private final ProductBatchClient products;
	private final InventoryStockRepository stock;
	private final PendingCheckoutReader pending;
	private final CheckoutQuote quotes;
	private final CartCheckoutItemsClient.Reader cart;

	public BuyNowPreviewService(CheckoutIdentityClient identity, ProductBatchClient products,
			InventoryStockRepository stock, PendingCheckoutReader pending, CheckoutQuote quotes,
			CartCheckoutItemsClient.Reader cart) {
		this.identity = identity;
		this.products = products;
		this.stock = stock;
		this.pending = pending;
		this.quotes = quotes;
		this.cart = cart;
	}

	public Preview preview(CurrentUser customer, String bearer, UUID productId, int quantity) {
		var customerContext = checkoutContext(customer, bearer);
		var blockers = customerContext.blockers();

		List<Issue> issues = new ArrayList<>();
		if (quantity > 99) {
			issues.add(new Issue(productId, null, "QUANTITY_LIMIT_EXCEEDED", null));
		}
		var matches = products.getProducts(List.of(productId));
		List<PreviewOrder> orders = List.of();
		long total = 0;
		String quote = null;
		if (matches.isEmpty() || !matches.getFirst().sellable() || !identity.sellerIsSellable(matches.getFirst().sellerId())) {
			issues.add(new Issue(productId, null, "PRODUCT_NOT_SELLABLE", null));
		} else {
			var product = matches.getFirst();
			if (product.priceVnd() <= 0 || quantity > Long.MAX_VALUE / product.priceVnd()) {
				throw new CheckoutIdentityClient.Unavailable();
			}
			Seller seller = identity.seller(product.sellerId());
			int available = stock.availability(List.of(productId)).stream().findFirst()
					.map(InventoryStockRepository.Availability::availableQuantity).orElse(0);
			if (quantity > available) {
				issues.add(new Issue(productId, null, "INSUFFICIENT_STOCK", available));
			}
			total = Math.multiplyExact(product.priceVnd(), quantity);
			orders = List.of(new PreviewOrder(seller, List.of(new Item(productId, product.productName(),
					product.coverImageUrl(), product.priceVnd(), quantity, total)), total));
			if (issues.isEmpty() && blockers.isEmpty()) {
				quote = quotes.issue(customer.userId(), productId, product.sellerId(), quantity, product.priceVnd());
			}
		}
		return new Preview("BUY_NOW", orders, 0, total, customerContext.address(), issues, blockers, quote != null, quote, true);
	}

	public Preview previewCart(CurrentUser customer, String bearer, List<UUID> cartItemIds) {
		var customerContext = checkoutContext(customer, bearer);
		var snapshot = cart.read(customer.userId(), cartItemIds);
		var quantityByProduct = new LinkedHashMap<UUID, Integer>();
		for (Line line : snapshot.items()) {
			quantityByProduct.merge(line.productId(), line.quantity(), Integer::sum);
		}
		var productIds = List.copyOf(quantityByProduct.keySet());
		var catalog = products.getProducts(productIds).stream()
				.collect(Collectors.toMap(ProductBatchClient.ProductInfo::productId, Function.identity()));
		if (catalog.size() != productIds.size()) {
			throw new CheckoutQuote.Unavailable();
		}
		var availability = stock.availability(productIds).stream().collect(Collectors.toMap(
				InventoryStockRepository.Availability::productId, InventoryStockRepository.Availability::availableQuantity));
		var sellers = new HashMap<UUID, Optional<Seller>>();
		var groups = new LinkedHashMap<Seller, List<Item>>();
		var issues = new ArrayList<Issue>();
		var quoteLines = new ArrayList<CheckoutQuote.CartLine>();
		try {
			for (Line line : snapshot.items()) {
				var product = catalog.get(line.productId());
				var seller = product.sellable()
						? sellers.computeIfAbsent(product.sellerId(), id -> identity.sellerIsSellable(id)
								? Optional.of(identity.seller(id)) : Optional.<Seller>empty())
						: Optional.<Seller>empty();
				if (seller.isEmpty()) {
					issues.add(new Issue(line.productId(), line.cartItemId(), "PRODUCT_NOT_SELLABLE", null));
					continue;
				}
				int available = availability.getOrDefault(line.productId(), 0);
				if (quantityByProduct.get(line.productId()) > available) {
					issues.add(new Issue(line.productId(), line.cartItemId(), "INSUFFICIENT_STOCK", available));
				}
				if (product.priceVnd() <= 0) {
					throw new CheckoutQuote.Unavailable();
				}
				long lineTotal = Math.multiplyExact(product.priceVnd(), line.quantity());
				groups.computeIfAbsent(seller.orElseThrow(), ignored -> new ArrayList<>()).add(new Item(line.productId(),
						product.productName(), product.coverImageUrl(), product.priceVnd(), line.quantity(), lineTotal));
				quoteLines.add(new CheckoutQuote.CartLine(line.cartItemId(), line.productId(), line.quantity(),
						line.rowVersion(), product.sellerId(), product.priceVnd()));
			}
			var orders = groups.entrySet().stream().map(group -> new PreviewOrder(group.getKey(), List.copyOf(group.getValue()),
					group.getValue().stream().mapToLong(Item::lineTotalVnd).reduce(0, Math::addExact))).toList();
			long total = orders.stream().mapToLong(PreviewOrder::orderTotalVnd).reduce(0, Math::addExact);
			var blockers = customerContext.blockers();
			boolean canPlaceOrder = issues.isEmpty() && blockers.isEmpty();
			String quote = canPlaceOrder ? quotes.issueCart(customer.userId(), snapshot.cartId(), quoteLines) : null;
			return new Preview("CART", orders, 0, total, customerContext.address(), issues, blockers, canPlaceOrder, quote, true);
		} catch (ArithmeticException overflow) {
			throw new CheckoutQuote.Unavailable();
		}
	}

	private CheckoutContext checkoutContext(CurrentUser customer, String bearer) {
		var eligibility = identity.eligibility(customer.userId());
		if (eligibility.authzVersion() != customer.authzVersion()) {
			throw new CheckoutIdentityClient.Unavailable();
		}
		Address address = identity.defaultAddress(bearer);
		if ((address != null) != (eligibility.addressCount() > 0)) {
			throw new CheckoutIdentityClient.Unavailable();
		}
		List<Blocker> blockers = new ArrayList<>();
		if (eligibility.paymentBlocked() || !"ACTIVE".equals(eligibility.customerStatus())
				|| !"ACTIVE".equals(eligibility.userStatus())) {
			blockers.add(new Blocker("CUSTOMER_PAYMENT_BLOCKED", null));
		}
		if (!eligibility.hasPhone() || address == null) {
			blockers.add(new Blocker("PROFILE_INCOMPLETE", null));
		}
		if (!eligibility.canCheckout() && blockers.isEmpty()) {
			throw new CheckoutIdentityClient.Unavailable();
		}
		pending.pendingSession(customer.userId()).ifPresent(id -> blockers.add(new Blocker("CHECKOUT_PENDING_EXISTS", id)));
		return new CheckoutContext(address, blockers);
	}

	private record CheckoutContext(Address address, List<Blocker> blockers) {}

	public record Preview(String source, List<PreviewOrder> orders, long shippingFeeVnd, long totalAmountVnd,
			Address defaultAddress, List<Issue> issues, List<Blocker> blockers, boolean canPlaceOrder, String quoteToken,
			boolean isSimulated) {
	}

	public record PreviewOrder(Seller seller, List<Item> items, long orderTotalVnd) {
	}

	public record Item(UUID productId, String productName, String coverImageUrl, long unitPriceVnd, int quantity,
			long lineTotalVnd) {
	}

	public record Issue(UUID productId, UUID cartItemId, String code, Integer availableQuantity) {
	}

	public record Blocker(String code, UUID checkoutSessionId) {
	}
}
