package com.fmatrestaurant.menu.infrastructure.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.fmatrestaurant.menu.domain.model.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

	List<Category> findByMenuId(Long menuId);

	Page<Category> findByMenuId(Long menuId, Pageable pageable);

	/** Every category of the menu in alphabetical order, the order of the visible catalog. */
	List<Category> findByMenuIdOrderByNameAscIdAsc(Long menuId);

}
