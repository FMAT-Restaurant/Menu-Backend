package com.fmatrestaurant.menu.api.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.fmatrestaurant.menu.api.ApiExceptionHandler.ErrorBody;
import com.fmatrestaurant.menu.api.ApiExceptionHandler.ErrorEnvelope;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** The 401 and 403 answers of the contract ({@code Unauthorized}, {@code Forbidden}), with the error envelope. */
public final class SecurityErrors {

	static final String UNAUTHORIZED = "A valid access token is required.";

	static final String FORBIDDEN = "The credential does not include the permission this operation requires.";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private SecurityErrors() {
	}

	/** 401: no credential, or one that is not a JWT or has a malformed {@code permissions} claim. */
	public static AuthenticationEntryPoint unauthorized() {
		return (request, response, exception) -> write(response, HttpStatus.UNAUTHORIZED, UNAUTHORIZED);
	}

	/** 403: the credential lacks a permission the operation requires; nothing was changed. */
	public static AccessDeniedHandler forbidden() {
		return (request, response, exception) -> write(response, HttpStatus.FORBIDDEN, FORBIDDEN);
	}

	static void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		JSON.writeValue(response.getOutputStream(), new ErrorEnvelope(new ErrorBody(message, null)));
	}

}
