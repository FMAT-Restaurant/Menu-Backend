package com.fmatrestaurant.menu.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fmatrestaurant.menu.domain.Image;
import com.fmatrestaurant.menu.domain.InvalidFieldException;
import com.fmatrestaurant.menu.infrastructure.ImageRepository;

/**
 * Upload and retrieval of catalog images. The backend validates the file and assigns its id.
 */
@Service
public class ImageService {

	private final ImageRepository imageRepository;

	public ImageService(ImageRepository imageRepository) {
		this.imageRepository = imageRepository;
	}

	/**
	 * @throws InvalidFieldException if the file is not a valid image
	 */
	@Transactional
	public Image upload(String declaredContentType, byte[] data) {
		return imageRepository.save(new Image(declaredContentType, data));
	}

	/**
	 * @throws ImageNotFoundException if the image does not exist
	 */
	@Transactional(readOnly = true)
	public Image get(UUID id) {
		return imageRepository.findById(id).orElseThrow(() -> new ImageNotFoundException(id));
	}

}
