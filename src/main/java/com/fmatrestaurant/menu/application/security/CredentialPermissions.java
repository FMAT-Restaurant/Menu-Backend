package com.fmatrestaurant.menu.application.security;

import java.util.Set;

/**
 * Reads the permissions that the credential of a request grants. Menu does not authenticate anyone nor
 * assign permissions: they come in the Bearer token issued by an external service (RF-GEN-01.CA4).
 */
public interface CredentialPermissions {

	/** Grants every permission; only administrators receive it. */
	String ALL = "*";

	/**
	 * @param bearerToken the token of the {@code Authorization: Bearer} header, without the scheme
	 * @return the permissions of the {@code permissions} claim; empty when the claim is missing
	 * @throws InvalidCredentialException if the token is not a JWT or {@code permissions} is not an array
	 *         of strings
	 */
	Set<String> permissionsOf(String bearerToken);

}
