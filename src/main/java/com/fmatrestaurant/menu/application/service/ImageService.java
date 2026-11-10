package com.fmatrestaurant.menu.application.service;

import java.util.UUID;

import com.fmatrestaurant.menu.application.exception.ImageNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fmatrestaurant.menu.domain.model.Image;
import com.fmatrestaurant.menu.domain.exception.InvalidFieldException;
import com.fmatrestaurant.menu.infrastructure.repository.ImageRepository;

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
