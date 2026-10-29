package com.example.identity_service.customer;

/**
 * G-14 decision taken at ban time: a customer with a paid order keeps access to old orders and only loses new
 * checkout and payment; one without loses all customer APIs.
 */
public enum CustomerBanMode {
	PAYMENT_ONLY, CUSTOMER_SCOPE
}
