package com.example.order_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

import com.example.order_service.client.PaymentCheckoutClient;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(clients = PaymentCheckoutClient.class)
public class OrderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderServiceApplication.class, args);
	}

}
