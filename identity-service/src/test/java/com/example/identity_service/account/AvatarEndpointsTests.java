package com.example.identity_service.account;

import com.example.identity_service.EndpointTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The account's avatar (API §3.9, §6.1 #11/#13, DB §3.1.1): {@code POST /media/upload-urls} with purpose
 * {@code AVATAR} and {@code account:update:own}, a real presigned PUT on SeaweedFS, then {@code avatarObjectKey} on
 * {@code PATCH /users/me}. A seller's avatar is its shop logo; there is no separate logo.
 */
class AvatarEndpointsTests extends EndpointTestSupport {

	private static final byte[] PNG = png(200);

	private final HttpClient http = HttpClient.newHttpClient();

	@Test
	void aCustomerUploadsAndAttachesAnAvatar() throws Exception {
		Session customer = customer();
		Instant now = clock.instant();

		String upload = body(call(HttpMethod.POST, "/media/upload-urls", avatarRequest("image/png", PNG.length),
				customer.accessToken())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].objectKey", startsWith("avatars/" + customer.userId() + "/"))));
		String objectKey = read(upload, "$.data[0].objectKey");
		assertThat(Instant.parse(read(upload, "$.data[0].expiresAt"))).isEqualTo(now.plus(Duration.ofMinutes(10)));
		assertThat(put(read(upload, "$.data[0].uploadUrl"), "image/png", PNG)).isEqualTo(200);

		String profile = body(call(HttpMethod.PATCH, "/users/me", json("avatarObjectKey", objectKey),
				customer.accessToken())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.avatarObjectKey").value(objectKey)));

		assertThat(download(read(profile, "$.data.avatarUrl"))).isEqualTo(PNG);
		call(HttpMethod.PATCH, "/users/me", "{\"avatarObjectKey\":null}", customer.accessToken())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.avatarObjectKey").isEmpty())
				.andExpect(jsonPath("$.data.avatarUrl").isEmpty());
	}

	@Test
	void aSellersAvatarIsItsShopLogo() throws Exception {
		Session seller = approvedSeller();
		String upload = body(call(HttpMethod.POST, "/media/upload-urls", avatarRequest("image/png", PNG.length),
				seller.accessToken()).andExpect(status().isOk()));
		put(read(upload, "$.data[0].uploadUrl"), "image/png", PNG);

		String profile = body(call(HttpMethod.PATCH, "/users/me",
				json("avatarObjectKey", read(upload, "$.data[0].objectKey")), seller.accessToken())
				.andExpect(status().isOk()));

		String logoUrl = read(profile, "$.data.sellerProfile.logoUrl");
		assertThat(logoUrl).isEqualTo(read(profile, "$.data.sellerProfile.avatarUrl"));
		assertThat(download(logoUrl)).isEqualTo(PNG);
		String publicLogo = read(body(call(HttpMethod.GET, "/sellers/" + seller.userId(), null, null)
				.andExpect(status().isOk())), "$.data.logoUrl");
		assertThat(download(publicLogo)).isEqualTo(PNG);
	}

	@Test
	void uploadUrlIsBoundToTheDeclaredSize() throws Exception {
		Session customer = customer();
		String upload = body(call(HttpMethod.POST, "/media/upload-urls", avatarRequest("image/png", PNG.length),
				customer.accessToken()));

		assertThat(put(read(upload, "$.data[0].uploadUrl"), "image/png", png(PNG.length + 50))).isNotEqualTo(200);
	}

	@Test
	void uploadRequestsOutsideTheAvatarRulesAreMediaInvalid() throws Exception {
		Session customer = customer();

		call(HttpMethod.POST, "/media/upload-urls", avatarRequest("image/gif", 1000), customer.accessToken())
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("MEDIA_INVALID"))
				.andExpect(jsonPath("$.error.details[0].field").value("files[0].mimeType"));
		call(HttpMethod.POST, "/media/upload-urls", avatarRequest("image/png", 3 * 1024 * 1024), customer.accessToken())
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.details[0].field").value("files[0].sizeBytes"));
		for (String purpose : new String[] { "SELLER_LOGO", "PRODUCT_MEDIA" }) {
			call(HttpMethod.POST, "/media/upload-urls", request("image/png", 100, purpose), customer.accessToken())
					.andExpect(status().isUnprocessableContent())
					.andExpect(jsonPath("$.error.details[0].field").value("files[0].purpose"));
		}
	}

	@Test
	void aBannedAccountCannotUploadOrChangeItsAvatar() throws Exception {
		Session seller = approvedSeller();
		jdbc.update("update sellers set status = 'BANNED', ban_reason = 'Khác: Fake goods' where seller_id = ?",
				seller.userId());

		call(HttpMethod.POST, "/media/upload-urls", avatarRequest("image/png", 100), seller.accessToken())
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ACCOUNT_BANNED"));
		call(HttpMethod.PATCH, "/users/me", "{\"avatarObjectKey\":null}", seller.accessToken())
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ACCOUNT_BANNED"));
	}

	@Test
	void theAvatarMustBeAnImageThisAccountUploaded() throws Exception {
		Session customer = customer();
		Session other = customer();
		byte[] notAnImage = "this is plain text, not a picture".getBytes();
		String upload = body(call(HttpMethod.POST, "/media/upload-urls", avatarRequest("image/png", notAnImage.length),
				customer.accessToken()));
		put(read(upload, "$.data[0].uploadUrl"), "image/png", notAnImage);
		String othersUpload = body(call(HttpMethod.POST, "/media/upload-urls", avatarRequest("image/png", PNG.length),
				other.accessToken()));
		put(read(othersUpload, "$.data[0].uploadUrl"), "image/png", PNG);

		expectAvatarRejected(customer, read(upload, "$.data[0].objectKey"), "NOT_AN_IMAGE");
		expectAvatarRejected(customer, read(othersUpload, "$.data[0].objectKey"), "NOT_OWNED");
		expectAvatarRejected(customer, "avatars/" + customer.userId() + "/missing.png", "NOT_FOUND");
	}

	private void expectAvatarRejected(Session session, String objectKey, String issue) throws Exception {
		call(HttpMethod.PATCH, "/users/me", json("avatarObjectKey", objectKey), session.accessToken())
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("MEDIA_INVALID"))
				.andExpect(jsonPath("$.error.details[0].field").value("avatarObjectKey"))
				.andExpect(jsonPath("$.error.details[0].issue").value(issue));
	}

	private int put(String url, String contentType, byte[] content) throws Exception {
		return http.send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", contentType)
				.PUT(HttpRequest.BodyPublishers.ofByteArray(content)).build(), HttpResponse.BodyHandlers.discarding())
				.statusCode();
	}

	private byte[] download(String url) throws Exception {
		HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(URI.create(url)).build(),
				HttpResponse.BodyHandlers.ofByteArray());
		assertThat(response.statusCode()).isEqualTo(200);
		return response.body();
	}

	private static String avatarRequest(String mimeType, long sizeBytes) {
		return request(mimeType, sizeBytes, "AVATAR");
	}

	private static String request(String mimeType, long sizeBytes, String purpose) {
		return "{\"files\":[{\"fileName\":\"avatar\",\"mimeType\":\"" + mimeType + "\",\"sizeBytes\":" + sizeBytes
				+ ",\"purpose\":\"" + purpose + "\"}]}";
	}

	/** A PNG signature followed by filler: enough for the magic-byte check. */
	private static byte[] png(int size) {
		byte[] bytes = new byte[size];
		Arrays.fill(bytes, (byte) 7);
		byte[] signature = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
		System.arraycopy(signature, 0, bytes, 0, signature.length);
		return bytes;
	}

}
