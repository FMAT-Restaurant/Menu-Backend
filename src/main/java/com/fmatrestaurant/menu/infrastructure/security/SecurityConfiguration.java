package com.fmatrestaurant.menu.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

import com.fmatrestaurant.menu.api.ApiVersioning;
import com.fmatrestaurant.menu.api.controller.PublicImageController;
import com.fmatrestaurant.menu.api.security.BearerPermissionsFilter;
import com.fmatrestaurant.menu.api.security.PermissionAuthorizationManager;
import com.fmatrestaurant.menu.api.security.SecurityErrors;
import com.fmatrestaurant.menu.application.security.CredentialPermissions;

/**
 * Every operation of Menu requires the permissions of its {@code x-required-permission} (RF-GEN-01). The
 * check runs in the security filter chain, before {@code IdempotencyFilter} and any write, so a request
 * without permission is answered 401 or 403 with no effects at all.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, CredentialPermissions credentials,
			PermissionAuthorizationManager permissions) {
		AuthenticationEntryPoint unauthorized = SecurityErrors.unauthorized();
		http
				// The CorsFilter registered first already answers preflights and adds the CORS headers.
				.cors(AbstractHttpConfigurer::disable)
				// CSRF does not apply: the credential travels only in the Authorization header, never in a cookie,
				// so a browser cannot attach it to a forged cross-site request. Sessions are not created either.
				.csrf(AbstractHttpConfigurer::disable) // NOSONAR java:S4502 stateless Bearer API without cookies
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.requestCache(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// Conditional GETs rely on ETags; the default no-store header of Spring Security would defeat them.
				.headers(headers -> headers.cacheControl(cache -> cache.disable()))
				.exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized)
						.accessDeniedHandler(SecurityErrors.forbidden()))
				.addFilterBefore(new BearerPermissionsFilter(credentials, unauthorized), AuthorizationFilter.class)
				.authorizeHttpRequests(requests -> requests
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers("/openapi.yaml", "/error").permitAll()
						// Image files are public, so a browser shows them with <img src> (url and thumbnailUrl).
						.requestMatchers(HttpMethod.GET, PublicImageController.PATH + "/*").permitAll()
						.requestMatchers(HttpMethod.HEAD, PublicImageController.PATH + "/*").permitAll()
						// Mock of Inventario (profile local): it stands for another service.
						.requestMatchers("/items", "/items/**", ApiVersioning.BASE + "/items",
								ApiVersioning.BASE + "/items/**").permitAll()
						.anyRequest().access(permissions));
		return http.build();
	}

	/** Menu authenticates nobody; without this bean Spring Boot would create a user with a password. */
	@Bean
	AuthenticationManager noAuthenticationManager() {
		return authentication -> {
			throw new ProviderNotFoundException("Menu does not authenticate users");
		};
	}

}
