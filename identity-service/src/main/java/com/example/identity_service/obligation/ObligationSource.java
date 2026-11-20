package com.example.identity_service.obligation;

import com.example.identity_service.user.User;

/**
 * Reads a customer or seller account's current obligations (API §7.1: Order's
 * {@code GET /internal/v1/orders/accounts/{userId}/obligations}, Finance's
 * {@code GET /internal/v1/finance/sellers/{sellerId}/obligations}). Called outside any transaction that holds locks.
 * A source that fails, times out, answers inconsistently or says 404 for an account that must exist throws
 * {@code ApiException(SERVICE_UNAVAILABLE)}: it is never read as "no obligations".
 */
public interface ObligationSource {

	AccountObligations of(User account);

}
