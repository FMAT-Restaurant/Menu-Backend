package com.fmatrestaurant.menu.api.security;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import com.fmatrestaurant.menu.api.ApiVersioning;
import com.fmatrestaurant.menu.api.security.OperationPermissions.Kind;
import com.fmatrestaurant.menu.api.security.OperationPermissions.Rule;
import com.fmatrestaurant.menu.api.security.PatchPermissionRule.Change;
import com.fmatrestaurant.menu.application.security.CredentialPermissions;
import com.fmatrestaurant.menu.application.service.CatalogOfferService;
import com.fmatrestaurant.menu.application.service.CatalogOfferService.CompositionShape;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Grants a request with a readable credential when its permissions include every permission its operation
 * requires, or {@code "*"}. A request without a credential is answered 401 by the entry point.
 */
@Component
public class PermissionAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final ObjectProvider<CatalogOfferService> offerService;

	/** Without the offer service (a web slice test), every composition in a PATCH counts as a management change. */
	public PermissionAuthorizationManager(ObjectProvider<CatalogOfferService> offerService) {
		this.offerService = offerService;
	}

	@Override
	public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
			RequestAuthorizationContext context) {
		Authentication current = authentication.get();
		if (current == null || !current.isAuthenticated() || !(current.getPrincipal() instanceof BearerPrincipal)) {
			return new AuthorizationDecision(false);
		}
		Set<String> granted = new HashSet<>();
		current.getAuthorities().stream().map(GrantedAuthority::getAuthority).forEach(granted::add);
		if (granted.contains(CredentialPermissions.ALL)) {
			return new AuthorizationDecision(true);
		}
		HttpServletRequest request = context.getRequest();
		Optional<Rule> rule = apiPath(request).flatMap(path -> OperationPermissions.find(request.getMethod(), path));
		// A route without an operation has no handler and changes nothing: it goes on to its 404 or 400.
		// OperationPermissionsContractTest makes sure every handler has a rule.
		return new AuthorizationDecision(rule.isEmpty() || granted.containsAll(required(rule.get(), request)));
	}

	/** The path relative to {@code /api/v1}, or empty if the request is outside the API. */
	static Optional<String> apiPath(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return path.startsWith(ApiVersioning.BASE + "/")
				? Optional.of(path.substring(ApiVersioning.BASE.length()))
				: Optional.empty();
	}

	private Set<String> required(Rule rule, HttpServletRequest request) {
		if (rule.kind() == Kind.FIXED) {
			return rule.permissions();
		}
		JsonNode body = body(request);
		Set<Change> changes = rule.kind() == Kind.ENTRY_PATCH
				? PatchPermissionRule.entryChanges(body)
				: PatchPermissionRule.offerChanges(body, offerId(request).flatMap(this::compositionShape));
		String resource = rule.kind() == Kind.ENTRY_PATCH ? "entries" : "offers";
		Set<String> required = new HashSet<>();
		if (changes.contains(Change.WRITE)) {
			required.add("menu:" + resource + ":write");
		}
		if (changes.contains(Change.STATUS)) {
			required.add("menu:" + resource + ":status");
		}
		return required;
	}

	private static JsonNode body(HttpServletRequest request) {
		CachedBodyHttpServletRequest cached = WebUtils.getNativeRequest(request, CachedBodyHttpServletRequest.class);
		if (cached == null) {
			return null;
		}
		try {
			return JSON.readTree(cached.body());
		} catch (JacksonException _) {
			return null;
		}
	}

	private Optional<List<CompositionShape>> compositionShape(UUID offerId) {
		CatalogOfferService service = offerService.getIfAvailable();
		return service == null ? Optional.empty() : service.compositionShape(offerId);
	}

	private static Optional<UUID> offerId(HttpServletRequest request) {
		return apiPath(request).map(path -> path.substring(path.lastIndexOf('/') + 1)).flatMap(id -> {
			try {
				return Optional.of(UUID.fromString(id));
			} catch (IllegalArgumentException _) {
				return Optional.empty();
			}
		});
	}

}
