package com.fmatrestaurant.menu;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;

/**
 * Sends the TK-ADMIN credential in every MockMvc request, so the tests of other features keep running as an
 * administrator. A test that sets {@code Authorization} itself overrides it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestSecurityConfiguration {

	@Bean
	MockMvcBuilderCustomizer adminCredentialByDefault() {
		return builder -> builder.defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION,
				TestTokens.bearer(TestTokens.ADMIN)));
	}

}
