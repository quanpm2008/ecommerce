package com.example.order_service;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CartCheckoutClientHttpTests {

	@Test
	void orderApplicationRegistersCartCheckoutFeignClient() {
		var registration = OrderServiceApplication.class.getAnnotation(EnableFeignClients.class);
		assertThat(Arrays.stream(registration.clients()).map(Class::getSimpleName))
				.contains("CartCheckoutItemsClient");
	}
}
