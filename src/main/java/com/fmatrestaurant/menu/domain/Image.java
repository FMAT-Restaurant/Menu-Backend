package com.fmatrestaurant.menu.domain;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Catalog image. Only JPEG, PNG and WebP files of up to 10 MiB and 4096 x 4096 px are accepted;
 * the real content is checked and decoded, not only the declared type.
 */
@Entity
@Table(name = "image")
public class Image {

	/** Maximum size of the file: 10 MiB. */
	public static final int MAX_BYTES = 10 * 1024 * 1024;

	/** Maximum width and height in pixels. */
	public static final int MAX_DIMENSION = 4096;

	private static final String PATH = "/file";

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "content_type", nullable = false, length = 32)
	private String contentType;

	@Column(nullable = false)
	private byte[] data;

	protected Image() {
		// Required by JPA.
	}

	/**
	 * Validates and keeps an image file.
	 *
	 * @throws InvalidFieldException if the file is empty, too big, of another type than declared,
	 *         not a supported format, larger than the maximum dimensions or not decodable
	 */
	public Image(String declaredContentType, byte[] data) {
		if (data == null || data.length == 0) {
			throw new InvalidFieldException(PATH, "The image file is required");
		}
		if (data.length > MAX_BYTES) {
			throw new InvalidFieldException(PATH, "The image must not exceed 10 MiB");
		}
		String realContentType = detectContentType(data);
		if (realContentType == null) {
			throw new InvalidFieldException(PATH, "The image must be a JPEG, PNG or WebP file");
		}
		if (!realContentType.equals(normalize(declaredContentType))) {
			throw new InvalidFieldException(PATH,
					"The declared type " + declaredContentType + " does not match the file content " + realContentType);
		}
		decode(realContentType.substring("image/".length()), data);
		this.contentType = realContentType;
		this.data = data;
	}

	private static String detectContentType(byte[] data) {
		String head = new String(data, 0, Math.min(data.length, 12), StandardCharsets.ISO_8859_1);
		if (head.startsWith("ÿØÿ")) {
			return "image/jpeg";
		}
		if (head.startsWith("\u0089PNG\r\n\u001A\n")) {
			return "image/png";
		}
		if (head.length() == 12 && head.startsWith("RIFF") && head.endsWith("WEBP")) {
			return "image/webp";
		}
		return null;
	}

	private static String normalize(String contentType) {
		if (contentType == null) {
			return null;
		}
		int parameters = contentType.indexOf(';');
		String type = parameters < 0 ? contentType : contentType.substring(0, parameters);
		return type.strip().toLowerCase(Locale.ROOT);
	}

	/** Checks the dimensions before decoding the pixels, so oversized images are never decoded. */
	private static void decode(String formatName, byte[] bytes) {
		Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(formatName);
		if (!readers.hasNext()) {
			throw new IllegalStateException("No image reader available for " + formatName);
		}
		ImageReader reader = readers.next();
		List<String> warnings = new ArrayList<>();
		reader.addIIOReadWarningListener((source, warning) -> warnings.add(warning));
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			reader.setInput(input, true, true);
			if (reader.getWidth(0) > MAX_DIMENSION || reader.getHeight(0) > MAX_DIMENSION) {
				throw new InvalidFieldException(PATH, "The image must not exceed " + MAX_DIMENSION + " x "
						+ MAX_DIMENSION + " px");
			}
			reader.read(0);
			if (!warnings.isEmpty()) {
				throw new InvalidFieldException(PATH, "The image could not be decoded: " + warnings.get(0));
			}
		} catch (InvalidFieldException e) {
			throw e;
		} catch (IOException | RuntimeException e) {
			throw new InvalidFieldException(PATH, "The image could not be decoded");
		} finally {
			reader.dispose();
		}
	}

	public UUID getId() {
		return id;
	}

	public String getContentType() {
		return contentType;
	}

	public byte[] getData() {
		return data;
	}

}
