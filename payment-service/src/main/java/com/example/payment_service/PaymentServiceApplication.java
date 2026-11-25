package com.example.payment_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.example.payment_service.client.OrderCheckoutPayableClient;
import com.example.payment_service.client.PaymentIdentityClient;
import com.example.payment_service.client.OrderCollectionReviewClient;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(clients = {OrderCheckoutPayableClient.class, PaymentIdentityClient.class, OrderCollectionReviewClient.class})
@EnableScheduling
public class PaymentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentServiceApplication.class, args);
	}

}
