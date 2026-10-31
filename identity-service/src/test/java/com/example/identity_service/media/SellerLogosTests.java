package com.example.identity_service.media;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SellerLogosTests {

	@Test
	void jpegPngAndWebpSignaturesAreAccepted() {
		assertThat(SellerLogos.isAllowedImage(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0, 0, 0, 0, 0, 0, 0))).isTrue();
		assertThat(SellerLogos.isAllowedImage(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0))).isTrue();
		assertThat(SellerLogos.isAllowedImage(bytes('R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P'))).isTrue();
	}

	@Test
	void otherContentIsRejected() {
		assertThat(SellerLogos.isAllowedImage(bytes('G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0, 0, 0))).isFalse();
		assertThat(SellerLogos.isAllowedImage(bytes('R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'A', 'V', 'E'))).isFalse();
		assertThat(SellerLogos.isAllowedImage(bytes(0xFF, 0xD8))).isFalse();
		assertThat(SellerLogos.isAllowedImage(new byte[0])).isFalse();
	}

	private static byte[] bytes(int... values) {
		byte[] bytes = new byte[values.length];
		for (int i = 0; i < values.length; i++) {
			bytes[i] = (byte) values[i];
		}
		return bytes;
	}

}
