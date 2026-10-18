package com.fmatrestaurant.menu.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fmatrestaurant.menu.application.CatalogEntryNotFoundException;
import com.fmatrestaurant.menu.application.CatalogOfferNotFoundException;
import com.fmatrestaurant.menu.application.CategoryNotFoundException;
import com.fmatrestaurant.menu.application.CategoryService;
import com.fmatrestaurant.menu.application.ImageNotFoundException;
import com.fmatrestaurant.menu.application.RecipeNotFoundException;
import com.fmatrestaurant.menu.domain.Category;

@WebMvcTest(controllers = CategoryController.class, properties = {
		"menu.cors.allowed-origins=https://menu.example.com, https://admin.example.com" })
@Import(CorsConfiguration.class)
class NotFoundAndCorsTest {

	private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
	private static final String PATH = "/api/v1/menu/categories/" + ID;

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private CategoryService service;

	static Stream<RuntimeException> missingResources() {
		return Stream.of(new CatalogEntryNotFoundException(ID), new CatalogOfferNotFoundException(ID),
				new CategoryNotFoundException(ID), new ImageNotFoundException(ID),
				new RecipeNotFoundException(ID), new RecipeNotFoundException(ID, 3));
	}

	@ParameterizedTest
	@MethodSource("missingResources")
	void explainsWhichResourceIsMissing(RuntimeException failure) throws Exception {
		when(service.get(ID)).thenThrow(failure);
		mvc.perform(get(PATH).header("Origin", "http://localhost:5173"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.message").value(failure.getMessage()))
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/v1/no-such-route", "/missing.js", "/api/v1/menu/categories/not-a-uuid" })
	void unknownRoutesAndMalformedIdsHaveJsonErrors(String path) throws Exception {
		mvc.perform(get(path).header("Origin", "http://localhost:3000"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.message").value("The requested resource was not found."))
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "http://localhost:3000", "http://localhost:5173", "https://localhost:9443",
			"http://localhost", "https://menu.example.com", "https://admin.example.com" })
	void acceptsPreflightForLocalhostAndConfiguredOrigins(String origin) throws Exception {
		mvc.perform(options(PATH).header("Origin", origin)
				.with(request -> {
					request.setServerName("api.example.com");
					return request;
				})
				.header("Access-Control-Request-Method", "PATCH")
				.header("Access-Control-Request-Headers", "content-type,authorization,if-match,if-none-match"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", origin))
				.andExpect(header().string("Access-Control-Max-Age", "3600"))
				.andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://evil.example", "http://localhost.evil.example:3000", "null",
			"http://menu.example.com", "https://menu.example.com:8443" })
	void rejectsUnlistedOrigins(String origin) throws Exception {
		mvc.perform(options(PATH).header("Origin", origin).header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	@Test
	void rejectsUnapprovedMethodsAndHeaders() throws Exception {
		mvc.perform(options(PATH).header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "PUT"))
				.andExpect(status().isForbidden());
		mvc.perform(options(PATH).header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "GET")
				.header("Access-Control-Request-Headers", "X-Unapproved"))
				.andExpect(status().isForbidden());
	}

	@Test
	void exposesValidatorsOnSuccessfulResponsesAndAllowsNonBrowserClients() throws Exception {
		when(service.get(ID)).thenReturn(new Category(CategoryService.DEFAULT_MENU_ID, "Drinks", null));
		mvc.perform(get(PATH).header("Origin", "https://menu.example.com"))
				.andExpect(status().isOk())
				.andExpect(header().string("ETag", "\"rev-0\""))
				.andExpect(header().string("Access-Control-Expose-Headers", "ETag, Location"));
		mvc.perform(get(PATH)).andExpect(status().isOk())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}
}
