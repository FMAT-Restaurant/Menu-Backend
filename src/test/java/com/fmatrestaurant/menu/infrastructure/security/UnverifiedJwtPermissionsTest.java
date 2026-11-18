package com.fmatrestaurant.menu.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fmatrestaurant.menu.TestTokens;
import com.fmatrestaurant.menu.application.security.InvalidCredentialException;

class UnverifiedJwtPermissionsTest {

	private final UnverifiedJwtPermissions credentials = new UnverifiedJwtPermissions();

	@Test
	@DisplayName("Lee la lista permissions del payload")
	void readsThePermissions() {
		assertEquals(Set.of("menu:entries:read", "menu:offers:read"),
				credentials.permissionsOf(TestTokens.with("menu:entries:read", "menu:offers:read")));
		assertEquals(Set.of("*"), credentials.permissionsOf(TestTokens.WILDCARD));
	}

	@Test
	@DisplayName("Sin permissions equivale a una lista vacía")
	void missingPermissionsIsAnEmptyList() {
		assertEquals(Set.of(), credentials.permissionsOf(TestTokens.WITHOUT_PERMISSIONS));
		assertEquals(Set.of(), credentials.permissionsOf(TestTokens.NONE));
	}

	@Test
	@DisplayName("permissions que no es un arreglo de strings es una credencial inválida")
	void malformedPermissionsAreInvalid() {
		assertThrows(InvalidCredentialException.class,
				() -> credentials.permissionsOf(TestTokens.MALFORMED_PERMISSIONS));
		assertThrows(InvalidCredentialException.class,
				() -> credentials.permissionsOf(token("{\"permissions\":[\"menu:entries:read\",1]}")));
	}

	@ParameterizedTest
	@ValueSource(strings = { "xyz", "a.b", "a..c", "a.!!!.c", "a.bm90LWpzb24.c", "a.WzFd.c" })
	@DisplayName("Lo que no es un JWT con un objeto JSON es una credencial inválida")
	void notAJwtIsInvalid(String token) {
		assertThrows(InvalidCredentialException.class, () -> credentials.permissionsOf(token));
	}

	private static String token(String payload) {
		Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
		return encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "."
				+ encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".";
	}

}
