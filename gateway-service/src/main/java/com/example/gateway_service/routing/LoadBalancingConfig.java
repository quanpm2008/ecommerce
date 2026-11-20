package com.example.gateway_service.routing;

import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Configuration;

/**
 * Every service's instance list comes straight from the discovery client (Eureka keeps its own local registry,
 * refreshed every 30 s): see {@link DiscoveryInstances}. Spring Cloud LoadBalancer's extra cache is left out (also
 * {@code spring.cloud.loadbalancer.cache.enabled=false}); it would only delay the moment a deregistered instance stops
 * getting traffic.
 */
@Configuration
@LoadBalancerClients(defaultConfiguration = DiscoveryInstances.class)
class LoadBalancingConfig {

}
