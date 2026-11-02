package com.example.gateway_service.routing;

import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Load-balancer configuration applied in each service's own child context (see {@link LoadBalancingConfig}): instances
 * from the blocking discovery client, without a cache. Deliberately not annotated and not nested in a configuration
 * class, so the main context never loads it (there it has no service id).
 */
class DiscoveryInstances {

	@Bean
	ServiceInstanceListSupplier discoveryClientServiceInstanceListSupplier(ConfigurableApplicationContext context) {
		return ServiceInstanceListSupplier.builder().withBlockingDiscoveryClient().build(context);
	}

}
