package com.fmatrestaurant.menu.api.controller;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.fmatrestaurant.menu.application.service.ImageService;
import com.fmatrestaurant.menu.domain.model.Image;

/**
 * The files of the images, public: no permission nor token, so a browser can show them with {@code <img src>}.
 * {@code url} and {@code thumbnailUrl} of every response point here. It is outside the versioned API and the
 * contract, like a CDN would be; {@code GET /api/v1/menu/media/images/{imageId}} keeps requiring
 * {@code menu:images:write}, as the contract says, to prepare the replacement of a file.
 */
@RestController
public class PublicImageController {

	/** Unversioned path of the public files; {@code SecurityConfiguration} lets anyone read it. */
	public static final String PATH = "/media/images";

	private final ImageService imageService;

	public PublicImageController(ImageService imageService) {
		this.imageService = imageService;
	}

	/**
	 * The ETag is a digest of the bytes, so it changes if the file is replaced (BE-17) while the id stays. Clients
	 * revalidate every time ({@code no-cache}) and an unchanged image answers 304 without its bytes.
	 */
	@GetMapping(PATH + "/{imageId}")
	public ResponseEntity<byte[]> file(@PathVariable UUID imageId) {
		Image image = imageService.get(imageId);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(image.getContentType()))
				.cacheControl(CacheControl.noCache().cachePublic())
				.eTag("\"" + sha256(image.getData()) + "\"")
				.body(image.getData());
	}

	private static String sha256(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("Every Java platform provides SHA-256", e);
		}
	}

}
