package com.example.identity_service.media;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The media bucket. Clients upload and download directly with presigned URLs; this service only signs them and
 * inspects what was uploaded. Storage errors become {@code SERVICE_UNAVAILABLE}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MediaStorage {

	private final S3Client s3;

	private final S3Presigner presigner;

	private final MediaProperties properties;

	private final Clock clock;

	private final AtomicBoolean bucketReady = new AtomicBoolean();

	/** A PUT URL bound to the declared MIME type and size; the client must send exactly those headers. */
	public PresignedUpload presignUpload(String objectKey, String mimeType, long sizeBytes) {
		ensureBucket();
		Instant expiresAt = clock.instant().plus(properties.uploadUrlTtl());
		String url = presigner.presignPutObject(request -> request
				.signatureDuration(properties.uploadUrlTtl())
				.putObjectRequest(PutObjectRequest.builder()
						.bucket(properties.bucket())
						.key(objectKey)
						.contentType(mimeType)
						.contentLength(sizeBytes)
						.build()))
				.url().toString();
		return new PresignedUpload(objectKey, url, expiresAt);
	}

	/** A time-limited GET URL for showing the object; no network call. */
	public String readUrl(String objectKey) {
		return presigner.presignGetObject(request -> request
				.signatureDuration(properties.readUrlTtl())
				.getObjectRequest(GetObjectRequest.builder().bucket(properties.bucket()).key(objectKey).build()))
				.url().toString();
	}

	/** Size and leading bytes of an uploaded object, or empty if it does not exist. */
	public Optional<StoredObject> inspect(String objectKey, int headerBytes) {
		try {
			HeadObjectResponse head = s3.headObject(request -> request.bucket(properties.bucket()).key(objectKey));
			byte[] header = s3.getObjectAsBytes(request -> request.bucket(properties.bucket()).key(objectKey)
					.range("bytes=0-" + (headerBytes - 1))).asByteArray();
			return Optional.of(new StoredObject(head.contentLength(), header));
		}
		catch (NoSuchKeyException | NoSuchBucketException ex) {
			return Optional.empty();
		}
		catch (S3Exception ex) {
			if (ex.statusCode() == 404) {
				return Optional.empty();
			}
			throw unavailable(ex);
		}
		catch (SdkException ex) {
			throw unavailable(ex);
		}
	}

	private void ensureBucket() {
		if (bucketReady.get()) {
			return;
		}
		try {
			s3.headBucket(request -> request.bucket(properties.bucket()));
		}
		catch (NoSuchBucketException ex) {
			createBucket();
		}
		catch (S3Exception ex) {
			if (ex.statusCode() != 404) {
				throw unavailable(ex);
			}
			createBucket();
		}
		catch (SdkException ex) {
			throw unavailable(ex);
		}
		bucketReady.set(true);
	}

	private void createBucket() {
		try {
			s3.createBucket(request -> request.bucket(properties.bucket()));
			log.info("Created media bucket {}", properties.bucket());
		}
		catch (SdkException ex) {
			throw unavailable(ex);
		}
	}

	private static ApiException unavailable(SdkException ex) {
		log.warn("Object storage call failed: {}", ex.getMessage());
		return new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Media storage is not available, please try again");
	}

	public record PresignedUpload(String objectKey, String uploadUrl, Instant expiresAt) {
	}

	public record StoredObject(long sizeBytes, byte[] header) {
	}

}
