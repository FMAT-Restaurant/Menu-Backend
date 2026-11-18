package com.fmatrestaurant.menu.api.security;

import java.io.IOException;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fmatrestaurant.menu.api.security.OperationPermissions.Kind;
import com.fmatrestaurant.menu.application.security.CredentialPermissions;
import com.fmatrestaurant.menu.application.security.InvalidCredentialException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads the permissions of the {@code Authorization: Bearer} credential into the security context. A request
 * without a credential continues anonymous, and authorization answers 401 if its route is protected; an
 * unreadable credential is answered with 401 at once. The body of the PATCH requests whose permissions depend
 * on it is kept, so authorization can read it.
 */
public class BearerPermissionsFilter extends OncePerRequestFilter {

	private static final String BEARER = "Bearer ";

	private final CredentialPermissions credentials;

	private final AuthenticationEntryPoint entryPoint;

	public BearerPermissionsFilter(CredentialPermissions credentials, AuthenticationEntryPoint entryPoint) {
		this.credentials = credentials;
		this.entryPoint = entryPoint;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null) {
			if (!header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
				entryPoint.commence(request, response, new InsufficientAuthenticationException("Not a Bearer token"));
				return;
			}
			Set<String> permissions;
			try {
				permissions = credentials.permissionsOf(header.substring(BEARER.length()).strip());
			} catch (InvalidCredentialException e) {
				entryPoint.commence(request, response, new InsufficientAuthenticationException(e.getMessage(), e));
				return;
			}
			var authentication = UsernamePasswordAuthenticationToken.authenticated(new BearerPrincipal(permissions),
					null, permissions.stream().map(SimpleGrantedAuthority::new).toList());
			var context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(authentication);
			SecurityContextHolder.setContext(context);
		}
		chain.doFilter(bodyDependent(request) ? new CachedBodyHttpServletRequest(request) : request, response);
	}

	private static boolean bodyDependent(HttpServletRequest request) {
		return PermissionAuthorizationManager.apiPath(request)
				.flatMap(path -> OperationPermissions.find(request.getMethod(), path))
				.filter(rule -> rule.kind() != Kind.FIXED)
				.isPresent();
	}

}
