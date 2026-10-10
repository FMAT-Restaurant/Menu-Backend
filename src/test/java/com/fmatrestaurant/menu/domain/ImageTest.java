package com.fmatrestaurant.menu.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class ImageTest {

	/** 1 x 1 lossless WebP; ImageIO can read WebP but not write it. */
	static final byte[] WEBP = Base64.getDecoder().decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==");

	static byte[] encode(String format, int width, int height) {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, output);
			return output.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Test
	void acceptsJpegPngAndWebp() {
		Image jpeg = new Image("image/jpeg", encode("jpg", 40, 30));
		Image png = new Image("image/png; charset=binary", encode("png", 4096, 1));
		Image webp = new Image("IMAGE/WEBP", WEBP);

		assertEquals("image/jpeg", jpeg.getContentType());
		assertEquals("image/png", png.getContentType());
		assertEquals("image/webp", webp.getContentType());
	}

	@Test
	void rejectsADeclaredTypeThatDoesNotMatchTheContent() {
		assertInvalid("image/jpeg", encode("png", 10, 10));
		assertInvalid(null, encode("png", 10, 10));
	}

	@Test
	void rejectsUnsupportedFormats() {
		assertInvalid("image/gif", encode("gif", 10, 10));
		assertInvalid("image/png", "not an image".getBytes());
	}

	@Test
	void rejectsEmptyFiles() {
		assertInvalid("image/png", new byte[0]);
		assertInvalid("image/png", null);
	}

	@Test
	void rejectsImagesWiderOrTallerThanTheLimit() {
		assertInvalid("image/png", encode("png", Image.MAX_DIMENSION + 1, 1));
		assertInvalid("image/png", encode("png", 1, Image.MAX_DIMENSION + 1));
	}

	@Test
	void rejectsFilesBiggerThanTenMebibytes() {
		byte[] png = encode("png", 10, 10);
		byte[] tooBig = Arrays.copyOf(png, Image.MAX_BYTES + 1);

		assertInvalid("image/png", tooBig);
	}

	@Test
	void rejectsFilesThatCannotBeDecoded() {
		byte[] png = encode("png", 64, 64);
		byte[] jpeg = encode("jpg", 64, 64);

		assertInvalid("image/png", Arrays.copyOf(png, png.length / 2));
		assertInvalid("image/jpeg", Arrays.copyOf(jpeg, jpeg.length / 2));
		assertInvalid("image/webp", Arrays.copyOf(WEBP, 16));
	}

	private static void assertInvalid(String contentType, byte[] data) {
		InvalidFieldException exception = assertThrows(InvalidFieldException.class,
				() -> new Image(contentType, data));
		assertEquals("/file", exception.getPath());
	}

}
