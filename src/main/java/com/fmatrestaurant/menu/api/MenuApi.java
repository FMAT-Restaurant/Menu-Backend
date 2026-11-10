package com.fmatrestaurant.menu.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A controller of the Menu API. Its paths are relative: {@link ApiVersioningConfiguration} prefixes them with
 * {@code /api/{version}} and the version below decides whether a request reaches it. To serve another version,
 * change it here or create a second annotation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RestController
@RequestMapping(version = ApiVersioning.CURRENT)
public @interface MenuApi {

	/** Path of the controller, without {@code /api/{version}}. */
	@AliasFor(annotation = RequestMapping.class, attribute = "path")
	String[] value() default {};
}
