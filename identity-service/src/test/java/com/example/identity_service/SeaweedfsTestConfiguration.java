package com.example.identity_service;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

/**
 * Throwaway SeaweedFS S3 server (same image as compose) with the dev credentials from application.properties.
 */
@TestConfiguration(proxyBeanMethods = false)
public class SeaweedfsTestConfiguration {

	private static final int S3_PORT = 8333;

	private static final String S3_CONFIG = """
			{"identities":[{"name":"test","credentials":[{"accessKey":"ecommerce","secretKey":"ecommerce-secret"}],
			"actions":["Admin","Read","Write","List","Tagging"]}]}""";

	@Bean
	GenericContainer<?> seaweedfs() {
		return new GenericContainer<>("chrislusf/seaweedfs:4.45")
				.withCommand("server", "-s3", "-s3.config=/etc/seaweedfs/s3.json", "-dir=/data", "-ip.bind=0.0.0.0")
				.withCopyToContainer(Transferable.of(S3_CONFIG), "/etc/seaweedfs/s3.json")
				.withExposedPorts(S3_PORT)
				.waitingFor(Wait.forHttp("/healthz").forPort(S3_PORT).forStatusCode(200));
	}

	@Bean
	DynamicPropertyRegistrar seaweedfsProperties(GenericContainer<?> seaweedfs) {
		return registry -> {
			registry.add("identity.media.endpoint", () -> url(seaweedfs));
			registry.add("identity.media.public-endpoint", () -> url(seaweedfs));
		};
	}

	private static String url(GenericContainer<?> seaweedfs) {
		return "http://" + seaweedfs.getHost() + ":" + seaweedfs.getMappedPort(S3_PORT);
	}

}
