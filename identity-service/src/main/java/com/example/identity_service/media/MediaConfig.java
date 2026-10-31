package com.example.identity_service.media;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * S3 clients for the media bucket. Path-style addressing and checksums only when required keep them compatible with
 * SeaweedFS and other S3-compatible servers. Building them does not touch the network.
 */
@Configuration(proxyBeanMethods = false)
public class MediaConfig {

	@Bean(destroyMethod = "close")
	S3Client s3Client(MediaProperties properties) {
		return S3Client.builder()
				.endpointOverride(URI.create(properties.endpoint()))
				.region(Region.of(properties.region()))
				.credentialsProvider(credentials(properties))
				.forcePathStyle(true)
				.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
				.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
				.build();
	}

	@Bean(destroyMethod = "close")
	S3Presigner s3Presigner(MediaProperties properties) {
		return S3Presigner.builder()
				.endpointOverride(URI.create(properties.publicEndpoint()))
				.region(Region.of(properties.region()))
				.credentialsProvider(credentials(properties))
				.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
				.build();
	}

	private static StaticCredentialsProvider credentials(MediaProperties properties) {
		return StaticCredentialsProvider.create(
				AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
	}

}
