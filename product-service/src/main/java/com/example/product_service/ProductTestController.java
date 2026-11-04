package com.example.product_service;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductTestController {

	@GetMapping("/api/products/test")
	public String test() {
		return "Product service is running 44444";
	}
}
