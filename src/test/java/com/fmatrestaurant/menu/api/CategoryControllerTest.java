package com.fmatrestaurant.menu.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.fmatrestaurant.menu.application.CategoryService;
import com.fmatrestaurant.menu.domain.Category;

class CategoryControllerTest {

	private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000007");

	@Test
	void emitsVersionZeroEtagForCategoryWithoutPersistedVersion() {
		CategoryService service = mock(CategoryService.class);
		Category category = new Category(CategoryService.DEFAULT_MENU_ID, "Drinks", null);
		when(service.get(ID)).thenReturn(category);
		when(service.entryCount(ID)).thenReturn(2L);

		ResponseEntity<DataResponse<CategoryController.CategoryItem>> response = new CategoryController(service).get(ID);

		assertEquals("\"rev-0\"", response.getHeaders().getETag());
		assertEquals(0, response.getBody().data().version());
	}

}
