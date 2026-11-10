package com.fmatrestaurant.menu.infrastructure.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fmatrestaurant.menu.domain.model.Image;

public interface ImageRepository extends JpaRepository<Image, UUID> {
}
