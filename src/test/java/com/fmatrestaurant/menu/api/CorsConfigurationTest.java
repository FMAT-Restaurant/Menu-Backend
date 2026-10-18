package com.fmatrestaurant.menu.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CorsFilter;

class CorsConfigurationTest {

	@Test
	void resolvesEnvironmentVariablesThroughApplicationProperties() throws Exception {
		var properties = new ResourcePropertySource("classpath:application.properties");
		new ApplicationContextRunner().withUserConfiguration(CorsConfiguration.class)
				.withInitializer(context -> {
					context.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource(
							"cors-test-env", Map.of("MENU_CORS_ALLOWED_ORIGINS", "https://env.example.com",
									"MENU_CORS_ALLOW_LOCALHOST", "false")));
					context.getEnvironment().getPropertySources().addLast(properties);
				}).run(context -> {
					MockMvc mvc = MockMvcBuilders.standaloneSetup(new InventoryMockController())
							.addFilters(context.getBean(CorsFilter.class)).build();
					mvc.perform(options("/inventory/items").header("Origin", "https://env.example.com")
							.header("Access-Control-Request-Method", "GET")).andExpect(status().isOk());
					mvc.perform(options("/inventory/items").header("Origin", "http://localhost:5173")
							.header("Access-Control-Request-Method", "GET")).andExpect(status().isForbidden());
				});
	}

	@ParameterizedTest
	@ValueSource(strings = { "*", "https://*.example.com", "https://example.com/path", "null",
			"https://example.com/", "https://user:password@example.com", "https://example.com?q=1",
			"https://example.com#fragment", "https://example.com:99999", "https://example.com:" })
	void rejectsUnsafeOrInvalidOriginConfiguration(String origin) {
		assertThrows(IllegalArgumentException.class, () -> new CorsConfiguration().corsFilter(origin, true));
	}

	@Test
	void canDisableLocalhostWhileRetainingASingleExplicitOrigin() throws Exception {
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new InventoryMockController())
				.addFilters(new CorsConfiguration().corsFilter("https://menu.example.com", false)).build();
		mvc.perform(options("/inventory/items").header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "GET")).andExpect(status().isForbidden());
		mvc.perform(options("/inventory/items").header("Origin", "https://menu.example.com")
				.header("Access-Control-Request-Method", "GET")).andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "https://menu.example.com"));
	}

	@Test
	void emptyConfigurationWithoutLocalhostDeniesCrossOriginRequests() throws Exception {
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new InventoryMockController())
				.addFilters(new CorsConfiguration().corsFilter("", false)).build();
		mvc.perform(options("/inventory/items").header("Origin", "https://menu.example.com")
				.header("Access-Control-Request-Method", "GET")).andExpect(status().isForbidden());
	}
}
