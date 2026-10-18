package com.fmatrestaurant.menu.api;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/** Central CORS policy, including errors and unknown routes. */
@Configuration(proxyBeanMethods = false)
public class CorsConfiguration {

	@Bean
	CorsFilter corsFilter(@Value("${menu.cors.allowed-origins:}") String origins,
			@Value("${menu.cors.allow-localhost:true}") boolean allowLocalhost) {
		var policy = new org.springframework.web.cors.CorsConfiguration();
		List<String> allowedOrigins = Arrays.stream(origins.split(","))
				.map(String::strip).filter(origin -> !origin.isEmpty()).toList();
		allowedOrigins.forEach(CorsConfiguration::validateOrigin);
		policy.setAllowedOrigins(allowedOrigins);
		if (allowLocalhost) {
			policy.setAllowedOriginPatterns(List.of("http://localhost:[*]", "https://localhost:[*]"));
		}
		policy.setAllowedMethods(List.of("GET", "HEAD", "POST", "PATCH", "DELETE", "OPTIONS"));
		policy.setAllowedHeaders(List.of("Accept", "Content-Type", "Authorization", "If-Match", "If-None-Match"));
		policy.setExposedHeaders(List.of("ETag", "Location"));
		policy.setAllowCredentials(false);
		policy.setMaxAge(3600L);
		var source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", policy);
		return new CorsFilter(source);
	}

	private static void validateOrigin(String origin) {
		URI uri = URI.create(origin);
		if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
				|| uri.getHost() == null || uri.getUserInfo() != null
				|| (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
				|| uri.getRawQuery() != null || uri.getRawFragment() != null
				|| uri.getPort() > 65535 || origin.endsWith(":")) {
			throw new IllegalArgumentException("menu.cors.allowed-origins requires exact HTTP(S) origins "
					+ "without paths, credentials or wildcards: " + origin);
		}
	}
}
