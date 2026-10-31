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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seller application, {@code PATCH /sellers/me} and logo upload through real presigned URLs on SeaweedFS
 * (API §3.9, §6.1, UC-A02, A06, checklist section 4).
 */
class SellerAccountEndpointsTests extends EndpointTestSupport {

	private static final byte[] PNG = png(200);

	private final HttpClient http = HttpClient.newHttpClient();

	// --- seller application ---

	@Test
	void customerAppliesToSellWithoutGettingTheSellerRole() throws Exception {
		Session customer = customer();
		String phone = uniquePhone();

		call(HttpMethod.GET, "/users/me/seller-application", null, customer.accessToken()).andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isEmpty());
		call(HttpMethod.POST, "/users/me/seller-application", application("Shop " + UUID.randomUUID(), phone),
				customer.accessToken())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.sellerId").value(customer.userId().toString()))
				.andExpect(jsonPath("$.data.approvalStatus").value("PENDING"))
				.andExpect(jsonPath("$.data.contactPhone").value(phone));

		call(HttpMethod.GET, "/users/me/seller-application", null, customer.accessToken()).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.approvalStatus").value("PENDING"));
		call(HttpMethod.GET, "/users/me", null, customer.accessToken()).andExpect(jsonPath("$.data.roles", contains("CUSTOMER")));
		assertThat(jdbc.queryForObject(
				"select count(*) from outbox_events where event_type = 'seller.registered' and aggregate_id = ?",
				Integer.class, customer.userId())).isEqualTo(1);
	}

	@Test
	void pendingOrApprovedApplicationCannotBeSubmittedAgain() throws Exception {
		Session pending = pendingSeller();
		Session approved = approvedSeller();

		call(HttpMethod.POST, "/users/me/seller-application", application("Shop " + UUID.randomUUID(), uniquePhone()),
				pending.accessToken())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"))
				.andExpect(jsonPath("$.error.details[0].currentStatus").value("PENDING"));
		call(HttpMethod.POST, "/users/me/seller-application", application("Shop " + UUID.randomUUID(), uniquePhone()),
				approved.accessToken())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.details[0].currentStatus").value("APPROVED"));
	}

	@Test
	void rejectedApplicationIsResubmittedWithTheSameSellerId() throws Exception {
		Session seller = pendingSeller();
		UUID reviewer = jdbc.queryForObject("""
				insert into users (email, auth_method, password_hash, full_name) values (?, 'LOCAL', 'x', 'Reviewer')
				returning user_id""", UUID.class, uniqueEmail());
		jdbc.update("""
				update sellers set approval_status = 'REJECTED', rejection_reason = 'Blurry documents',
				reviewed_at = now(), reviewed_by_user_id = ? where seller_id = ?""", reviewer, seller.userId());
		String newName = "Shop " + UUID.randomUUID();

		call(HttpMethod.POST, "/users/me/seller-application", application(newName, uniquePhone()), seller.accessToken())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.sellerId").value(seller.userId().toString()))
				.andExpect(jsonPath("$.data.sellerName").value(newName))
				.andExpect(jsonPath("$.data.approvalStatus").value("PENDING"))
				.andExpect(jsonPath("$.data.rejectionReason").isEmpty())
				.andExpect(jsonPath("$.data.reviewedAt").isEmpty());
	}

	@Test
	void applicationAcceptsOnlyItsOwnFields() throws Exception {
		Session customer = customer();

		call(HttpMethod.POST, "/users/me/seller-application",
				"{\"sellerName\":\"Shop X\",\"contactPhone\":\"" + uniquePhone()
						+ "\",\"userId\":\"" + UUID.randomUUID() + "\",\"status\":\"ACTIVE\",\"approvalStatus\":\"APPROVED\"}",
				customer.accessToken())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[*].field",
						org.hamcrest.Matchers.containsInAnyOrder("userId", "status", "approvalStatus")));
		call(HttpMethod.GET, "/users/me/seller-application", null, customer.accessToken())
				.andExpect(jsonPath("$.data").isEmpty());
	}

	@Test
	void applicationRejectsATakenShopName() throws Exception {
		String name = "Shop " + UUID.randomUUID();
		registerSeller(uniqueEmail(), name, uniquePhone()).andExpect(status().isCreated());

		call(HttpMethod.POST, "/users/me/seller-application", application(name.toLowerCase(), uniquePhone()),
				customer().accessToken())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("SELLER_NAME_ALREADY_USED"));
	}

	// --- PATCH /sellers/me ---

	@Test
	void approvedSellerUpdatesDescriptionAndContactPhone() throws Exception {
		Session seller = approvedSeller();
		String phone = uniquePhone();

		call(HttpMethod.PATCH, "/sellers/me", json("description", "Handmade clothes", "contactPhone", phone),
				seller.accessToken())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.description").value("Handmade clothes"))
				.andExpect(jsonPath("$.data.contactPhone").value(phone));
	}

	@Test
	void sellerNameAndEmailCannotBeChanged() throws Exception {
		Session seller = approvedSeller();

		call(HttpMethod.PATCH, "/sellers/me", json("sellerName", "Renamed", "email", "x@example.test"),
				seller.accessToken())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[*].field",
						org.hamcrest.Matchers.containsInAnyOrder("sellerName", "email")));
	}

	@Test
	void contactPhoneOfAnotherActiveSellerIsRejected() throws Exception {
		Session seller = approvedSeller();
		Session other = pendingSeller();
		String taken = jdbc.queryForObject("select contact_phone from sellers where seller_id = ?", String.class,
				other.userId());

		call(HttpMethod.PATCH, "/sellers/me", json("contactPhone", taken), seller.accessToken())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("PHONE_ALREADY_USED"));
	}

	@Test
	void onlyAnApprovedActiveSellerMayEditTheShop() throws Exception {
		Session pending = pendingSeller();
		Session banned = approvedSeller();
		jdbc.update("update sellers set status = 'BANNED', ban_reason = 'Fake goods' where seller_id = ?",
				banned.userId());

		call(HttpMethod.PATCH, "/sellers/me", json("description", "x"), pending.accessToken())
				.andExpect(status().isForbidden());
		call(HttpMethod.PATCH, "/sellers/me", json("description", "x"), banned.accessToken())
				.andExpect(status().isForbidden());
	}

	// --- logo upload ---

	@Test
	void logoIsUploadedWithAPresignedUrlAndAttached() throws Exception {
		Session seller = approvedSeller();
		Instant now = clock.instant();

		String upload = body(call(HttpMethod.POST, "/media/upload-urls", logoRequest("image/png", PNG.length),
				seller.accessToken())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].objectKey", startsWith("seller-logos/" + seller.userId() + "/"))));
		String objectKey = read(upload, "$.data[0].objectKey");
		assertThat(Instant.parse(read(upload, "$.data[0].expiresAt"))).isEqualTo(now.plus(Duration.ofMinutes(10)));

		assertThat(put(read(upload, "$.data[0].uploadUrl"), "image/png", PNG)).isEqualTo(200);
		String profile = body(call(HttpMethod.PATCH, "/sellers/me", json("logoObjectKey", objectKey), seller.accessToken())
				.andExpect(status().isOk()));

		HttpResponse<byte[]> logo = http.send(HttpRequest.newBuilder(URI.create(read(profile, "$.data.logoUrl"))).build(),
				HttpResponse.BodyHandlers.ofByteArray());
		assertThat(logo.statusCode()).isEqualTo(200);
		assertThat(logo.body()).isEqualTo(PNG);
		assertThat(jdbc.queryForObject("select logo_object_key from sellers where seller_id = ?", String.class,
				seller.userId())).isEqualTo(objectKey);
	}

	@Test
	void uploadUrlIsBoundToTheDeclaredSize() throws Exception {
		Session seller = approvedSeller();
		String upload = body(call(HttpMethod.POST, "/media/upload-urls", logoRequest("image/png", PNG.length),
				seller.accessToken()));

		assertThat(put(read(upload, "$.data[0].uploadUrl"), "image/png", png(PNG.length + 50)))
				.isNotEqualTo(200);
	}

	@Test
	void uploadRequestsOutsideTheLogoRulesAreMediaInvalid() throws Exception {
		Session seller = approvedSeller();

		call(HttpMethod.POST, "/media/upload-urls", logoRequest("image/gif", 1000), seller.accessToken())
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("MEDIA_INVALID"))
				.andExpect(jsonPath("$.error.details[0].field").value("files[0].mimeType"));
		call(HttpMethod.POST, "/media/upload-urls", logoRequest("image/png", 3 * 1024 * 1024), seller.accessToken())
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.details[0].field").value("files[0].sizeBytes"));
		call(HttpMethod.POST, "/media/upload-urls",
				"{\"files\":[{\"fileName\":\"a.png\",\"mimeType\":\"image/png\",\"sizeBytes\":100,\"purpose\":\"PRODUCT_MEDIA\"}]}",
				seller.accessToken())
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.details[0].field").value("files[0].purpose"));
	}

	@Test
	void uploadUrlsNeedAnOperatingSeller() throws Exception {
		call(HttpMethod.POST, "/media/upload-urls", logoRequest("image/png", 100), pendingSeller().accessToken())
				.andExpect(status().isForbidden());
		call(HttpMethod.POST, "/media/upload-urls", logoRequest("image/png", 100), customer().accessToken())
				.andExpect(status().isForbidden());
	}

	@Test
	void logoMustBeAnImageThisSellerUploaded() throws Exception {
		Session seller = approvedSeller();
		Session other = approvedSeller();

		byte[] notAnImage = "this is plain text, not a picture".getBytes();
		String upload = body(call(HttpMethod.POST, "/media/upload-urls", logoRequest("image/png", notAnImage.length),
				seller.accessToken()));
		put(read(upload, "$.data[0].uploadUrl"), "image/png", notAnImage);
		String othersUpload = body(call(HttpMethod.POST, "/media/upload-urls", logoRequest("image/png", PNG.length),
				other.accessToken()));
		put(read(othersUpload, "$.data[0].uploadUrl"), "image/png", PNG);

		expectLogoRejected(seller, read(upload, "$.data[0].objectKey"), "NOT_AN_IMAGE");
		expectLogoRejected(seller, read(othersUpload, "$.data[0].objectKey"), "NOT_OWNED");
		expectLogoRejected(seller, "seller-logos/" + seller.userId() + "/missing.png", "NOT_FOUND");
	}

	private void expectLogoRejected(Session seller, String objectKey, String issue) throws Exception {
		call(HttpMethod.PATCH, "/sellers/me", json("logoObjectKey", objectKey), seller.accessToken())
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("MEDIA_INVALID"))
				.andExpect(jsonPath("$.error.details[0].issue").value(issue));
	}

	private int put(String url, String contentType, byte[] content) throws Exception {
		return http.send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", contentType)
				.PUT(HttpRequest.BodyPublishers.ofByteArray(content)).build(), HttpResponse.BodyHandlers.discarding())
				.statusCode();
	}

	private static String application(String sellerName, String contactPhone) {
		return json("sellerName", sellerName, "contactPhone", contactPhone, "description", "We sell things");
	}

	private static String logoRequest(String mimeType, long sizeBytes) {
		return "{\"files\":[{\"fileName\":\"logo\",\"mimeType\":\"" + mimeType + "\",\"sizeBytes\":" + sizeBytes
				+ ",\"purpose\":\"SELLER_LOGO\"}]}";
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
