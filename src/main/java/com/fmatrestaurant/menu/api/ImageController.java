package com.fmatrestaurant.menu.api;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.fmatrestaurant.menu.application.ImageService;
import com.fmatrestaurant.menu.domain.Image;

@RestController
@RequestMapping("/api/v1/media/images")
public class ImageController {

	private final ImageService imageService;

	public ImageController(ImageService imageService) {
		this.imageService = imageService;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<DataResponse<ImageResponse>> upload(@RequestPart("file") MultipartFile file)
			throws IOException {
		Image image = imageService.upload(file.getContentType(), file.getBytes());
		return ResponseEntity.status(HttpStatus.CREATED)
				.eTag(etag(image))
				.body(new DataResponse<>(ImageResponse.of(image.getId())));
	}

	/** Serves the file. Not part of the contract, but it is where {@code url} points to. */
	@GetMapping("/{imageId}")
	public ResponseEntity<byte[]> get(@PathVariable UUID imageId) {
		Image image = imageService.get(imageId);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(image.getContentType()))
				.eTag(etag(image))
				.body(image.getData());
	}

	/** Images never change, so their id identifies the representation. */
	private static String etag(Image image) {
		return "\"" + image.getId() + "\"";
	}

	public record ImageResponse(UUID id, String url, String thumbnailUrl) {

		static ImageResponse of(UUID id) {
			String url = ServletUriComponentsBuilder.fromCurrentContextPath()
					.path("/api/v1/media/images/{id}").buildAndExpand(id).toUriString();
			// ponytail: no thumbnails are generated yet, the original is served for both.
			return new ImageResponse(id, url, url);
		}

	}

}
