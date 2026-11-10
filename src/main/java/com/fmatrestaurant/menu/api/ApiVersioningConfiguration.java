package com.fmatrestaurant.menu.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.RequestPath;
import org.springframework.web.accept.ApiVersionParser;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Native API versioning of Spring Framework 7: the version is the second segment of {@code /api/{version}/...},
 * it is required there, and a version no controller declares is rejected before reaching one. Paths outside
 * {@code /api} (documentation, errors, the local Inventory mock) are not versioned.
 */
@Configuration(proxyBeanMethods = false)
public class ApiVersioningConfiguration implements WebMvcConfigurer {

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.addPathPrefix(ApiVersioning.PREFIX + "/{version}", HandlerTypePredicate.forAnnotation(MenuApi.class));
	}

	@Override
	public void configureApiVersioning(ApiVersionConfigurer configurer) {
		// Not required globally: a path outside /api (the local Inventory mock, /items/{id}) carries no version
		// and must still reach its handler. Under /api the segment is always read, so a missing or unknown
		// version there is still rejected.
		configurer.usePathSegment(1, ApiVersioningConfiguration::isVersioned)
				.setVersionParser(new PrefixedVersionParser())
				.setVersionRequired(false);
	}

	private static boolean isVersioned(RequestPath path) {
		return path.value().startsWith(ApiVersioning.PREFIX + "/");
	}

	/** Reads {@code v1} (or {@code 1}) as the number 1; the default parser does not accept the {@code v}. */
	static final class PrefixedVersionParser implements ApiVersionParser<Integer> {

		@Override
		public Integer parseVersion(String version) {
			String number = version.startsWith("v") || version.startsWith("V") ? version.substring(1) : version;
			if (number.isEmpty() || !number.chars().allMatch(Character::isDigit)) {
				throw new IllegalArgumentException("Not a version: " + version);
			}
			return Integer.valueOf(number);
		}
	}
}
