package com.fmatrestaurant.menu.api.security;

import java.util.Set;

/** Whoever sends a readable Bearer credential, with the permissions it grants. */
public record BearerPrincipal(Set<String> permissions) {
}
