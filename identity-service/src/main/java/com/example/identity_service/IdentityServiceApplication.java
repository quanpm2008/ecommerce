package com.example.identity_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import com.example.identity_service.internal.SellerObligations;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(clients = {SellerObligations.Orders.class, SellerObligations.Finance.class})
@ConfigurationPropertiesScan
public class IdentityServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(IdentityServiceApplication.class, args);
	}

}
