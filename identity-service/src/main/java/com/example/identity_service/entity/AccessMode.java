package com.example.identity_service.entity;

/**
 * What an account may still do (API §4.2, §3.2 G-14). FULL unless banned. A BANNED account keeps a limited session
 * for its own open obligations only; DISABLED has none.
 * <ul>
 * <li>{@code OPEN_ORDERS_ONLY}: open orders (or, for a customer, a refund it may retry)</li>
 * <li>{@code ORDERS_AND_WITHDRAWAL}: a seller with open orders and an available balance</li>
 * <li>{@code WITHDRAWAL_ONLY}: a seller with only an available balance</li>
 * <li>{@code OBLIGATIONS_READ_ONLY}: only things the system is still settling (pending payment, settlement, pending
 * balance): read, no action</li>
 * <li>{@code LOGIN_BLOCKED}: DISABLED, nothing left</li>
 * </ul>
 */
public enum AccessMode {
	FULL, OPEN_ORDERS_ONLY, ORDERS_AND_WITHDRAWAL, WITHDRAWAL_ONLY, OBLIGATIONS_READ_ONLY, LOGIN_BLOCKED
}
