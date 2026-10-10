package com.fmatrestaurant.menu.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fmatrestaurant.menu.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

	List<Category> findByMenuId(Long menuId);

}
