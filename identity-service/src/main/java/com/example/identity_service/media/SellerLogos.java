package com.example.identity_service.media;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Rules for {@code SELLER_LOGO} media (API §3.9, P-08): JPG, PNG or WebP up to {@code sellerLogoMaxBytes}, stored
 * under {@code seller-logos/<sellerId>/}. The prefix proves ownership; the leading bytes prove the real type.
 */
@Component
@RequiredArgsConstructor
public class SellerLogos {

	public static final String PURPOSE = "SELLER_LOGO";

	private static final Map<String, String> EXTENSION_BY_MIME = Map.of("image/jpeg", "jpg", "image/png", "png",
			"image/webp", "webp");

	private static final int HEADER_BYTES = 12;

	private final MediaStorage storage;

	private final MediaProperties properties;

	/** Checks a declared upload and returns the object key to sign for. */
	public String newObjectKey(UUID sellerId, String purpose, String mimeType, long sizeBytes) {
		if (!PURPOSE.equals(purpose)) {
			throw ApiException.forField(ErrorCode.MEDIA_INVALID, "files[0].purpose", "UNSUPPORTED_PURPOSE");
		}
		String extension = EXTENSION_BY_MIME.get(mimeType);
		if (extension == null) {
			throw ApiException.forField(ErrorCode.MEDIA_INVALID, "files[0].mimeType", "UNSUPPORTED_TYPE");
		}
		if (sizeBytes <= 0 || sizeBytes > properties.sellerLogoMaxBytes()) {
			throw ApiException.forField(ErrorCode.MEDIA_INVALID, "files[0].sizeBytes", "TOO_LARGE");
		}
		return prefix(sellerId) + UUID.randomUUID() + "." + extension;
	}

	/** Verifies that {@code objectKey} was uploaded by this seller and really is an allowed image. */
	public void requireUploadedLogo(UUID sellerId, String objectKey) {
		if (!objectKey.startsWith(prefix(sellerId)) || objectKey.contains("..")) {
			throw ApiException.forField(ErrorCode.MEDIA_INVALID, "logoObjectKey", "NOT_OWNED");
		}
		MediaStorage.StoredObject object = storage.inspect(objectKey, HEADER_BYTES)
				.orElseThrow(() -> ApiException.forField(ErrorCode.MEDIA_INVALID, "logoObjectKey", "NOT_FOUND"));
		if (object.sizeBytes() > properties.sellerLogoMaxBytes()) {
			throw ApiException.forField(ErrorCode.MEDIA_INVALID, "logoObjectKey", "TOO_LARGE");
		}
		if (!isAllowedImage(object.header())) {
			throw ApiException.forField(ErrorCode.MEDIA_INVALID, "logoObjectKey", "NOT_AN_IMAGE");
		}
	}

	private static String prefix(UUID sellerId) {
		return "seller-logos/" + sellerId + "/";
	}

	/** Magic bytes: JPEG FF D8 FF, PNG 89 50 4E 47 0D 0A 1A 0A, WebP "RIFF" .... "WEBP". */
	static boolean isAllowedImage(byte[] header) {
		return startsWith(header, 0, 0xFF, 0xD8, 0xFF)
				|| startsWith(header, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
				|| (startsWith(header, 0, 'R', 'I', 'F', 'F') && startsWith(header, 8, 'W', 'E', 'B', 'P'));
	}

	private static boolean startsWith(byte[] bytes, int offset, int... expected) {
		if (bytes.length < offset + expected.length) {
			return false;
		}
		return IntStream.range(0, expected.length).allMatch(i -> (bytes[offset + i] & 0xFF) == expected[i]);
	}

}
