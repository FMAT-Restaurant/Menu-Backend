package com.fmatrestaurant.menu.infrastructure;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fmatrestaurant.menu.domain.Image;

public interface ImageRepository extends JpaRepository<Image, UUID> {

}
