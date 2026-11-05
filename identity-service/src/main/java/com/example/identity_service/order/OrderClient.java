package com.example.identity_service.order;

import java.util.UUID;

/**
 * What Identity asks Order (API §7.1): whether a customer has a paid order, for the G-14 decision at ban time.
 * Identity never reads Order's database. Implementations throw {@code ApiException(SERVICE_UNAVAILABLE)} when Order
 * does not give a usable answer.
 */
public interface OrderClient {

	boolean hasPaidOrder(UUID customerId);

}
