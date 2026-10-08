package com.fmatrestaurant.menu.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fmatrestaurant.menu.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {

	List<Category> findByMenuId(Long menuId);

}
