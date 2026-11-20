package com.example.order_service.checkout;

import com.example.order_service.client.CheckoutIdentityClient;
import com.example.order_service.client.CheckoutIdentityClient.Address;
import com.example.order_service.client.CheckoutIdentityClient.Seller;
import com.example.order_service.client.ProductBatchClient;
import com.example.order_service.inventory.InventoryStockRepository;
import com.example.order_service.security.OrderAuthorization.CurrentUser;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class BuyNowPreviewService {
	private final CheckoutIdentityClient identity;
	private final ProductBatchClient products;
	private final InventoryStockRepository stock;
	private final PendingCheckoutReader pending;
	private final CheckoutQuote quotes;

	public BuyNowPreviewService(CheckoutIdentityClient identity, ProductBatchClient products,
			InventoryStockRepository stock, PendingCheckoutReader pending, CheckoutQuote quotes) {
		this.identity = identity;
		this.products = products;
		this.stock = stock;
		this.pending = pending;
		this.quotes = quotes;
	}

	public Preview preview(CurrentUser customer, String bearer, UUID productId, int quantity) {
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
		return new Preview("BUY_NOW", orders, 0, total, address, issues, blockers, quote != null, quote, true);
	}

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
