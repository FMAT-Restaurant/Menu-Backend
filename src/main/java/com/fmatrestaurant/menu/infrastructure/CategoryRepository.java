package com.fmatrestaurant.menu.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.fmatrestaurant.menu.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

	List<Category> findByMenuId(Long menuId);

	Page<Category> findByMenuId(Long menuId, Pageable pageable);

	boolean existsByMenuIdAndName(Long menuId, String name);

	boolean existsByMenuIdAndNameAndIdNot(Long menuId, String name, UUID id);

}
