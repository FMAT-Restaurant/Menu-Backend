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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.fmatrestaurant.menu.api.ApiVersioning;
import com.fmatrestaurant.menu.api.MenuApi;
import com.fmatrestaurant.menu.application.service.ImageService;
import com.fmatrestaurant.menu.domain.model.Image;

/**
 * The files of the images, public: no permission nor token, so a browser can show them with {@code <img src>}.
 * They live in the API with its common prefixes, next to the image ({@code /api/v1/menu/media/images/{id}/file}),
 * and {@code url} and {@code thumbnailUrl} of every response point here. The contract does not define this route;
 * getMenuImage ({@code GET /api/v1/menu/media/images/{imageId}}) keeps requiring {@code menu:images:write}.
 */
@MenuApi(PublicImageController.IMAGES)
public class PublicImageController {

	/** Path of the images, relative to the API version. */
	static final String IMAGES = "/menu/media/images";

	/** Path of the file of an image, relative to the API version. */
	public static final String FILE = IMAGES + "/{imageId}/file";

	/** Ant pattern of the public files, for {@code SecurityConfiguration}. */
	public static final String PUBLIC_PATTERN = ApiVersioning.BASE + IMAGES + "/*/file";

	private final ImageService imageService;

	public PublicImageController(ImageService imageService) {
		this.imageService = imageService;
	}

	/** The absolute URL of the file of an image, from the current request. */
	static String url(UUID imageId) {
		return ServletUriComponentsBuilder.fromCurrentContextPath()
				.path(ApiVersioning.BASE + FILE).buildAndExpand(imageId).toUriString();
	}

	/**
	 * The ETag is a digest of the bytes, so it changes if the file is replaced (BE-17) while the id stays. Clients
	 * revalidate every time ({@code no-cache}) and an unchanged image answers 304 without its bytes.
	 */
	@GetMapping("/{imageId}/file")
	public ResponseEntity<byte[]> download(@PathVariable UUID imageId) {
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
