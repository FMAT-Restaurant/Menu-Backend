package com.fmatrestaurant.menu.infrastructure.security;

import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fmatrestaurant.menu.application.security.CredentialPermissions;
import com.fmatrestaurant.menu.application.security.InvalidCredentialException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads {@code permissions} from the payload of the JWT <strong>without verifying its signature</strong>.
 * Until BE-12 replaces it with a verifying implementation, Menu must not be exposed without the Gateway in
 * front of it.
 */
@Component
public class UnverifiedJwtPermissions implements CredentialPermissions {

	private static final Logger LOG = LoggerFactory.getLogger(UnverifiedJwtPermissions.class);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	public UnverifiedJwtPermissions() {
		LOG.warn("JWT signatures are NOT verified (BE-12 pending): do not expose Menu without the Gateway.");
	}

	@Override
	public Set<String> permissionsOf(String bearerToken) {
		String[] parts = bearerToken.split("[.]", -1);
		if (parts.length != 3 || parts[1].isEmpty()) {
			throw new InvalidCredentialException("The credential is not a JWT");
		}
		JsonNode payload;
		try {
			payload = JSON.readTree(Base64.getUrlDecoder().decode(parts[1]));
		} catch (IllegalArgumentException | JacksonException _) {
			throw new InvalidCredentialException("The payload of the JWT is not valid Base64URL JSON");
		}
		if (payload == null || !payload.isObject()) {
			throw new InvalidCredentialException("The payload of the JWT is not a JSON object");
		}
		JsonNode permissions = payload.get("permissions");
		if (permissions == null || permissions.isNull()) {
			return Set.of();
		}
		if (!permissions.isArray()) {
			throw new InvalidCredentialException("The permissions claim must be an array of strings");
		}
		Set<String> granted = new LinkedHashSet<>();
		for (JsonNode permission : permissions) {
			if (!permission.isString()) {
				throw new InvalidCredentialException("The permissions claim must be an array of strings");
			}
			granted.add(permission.stringValue());
		}
		return Set.copyOf(granted);
	}

}
