package com.fmatrestaurant.menu;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

import com.fmatrestaurant.menu.api.security.PermissionAuthorizationManager;
import com.fmatrestaurant.menu.infrastructure.security.SecurityConfiguration;
import com.fmatrestaurant.menu.infrastructure.security.UnverifiedJwtPermissions;

/**
 * The security of Menu for {@code @WebMvcTest} slices, which do not scan it, with the TK-ADMIN credential
 * sent by default.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({ SecurityConfiguration.class, UnverifiedJwtPermissions.class, PermissionAuthorizationManager.class,
		TestSecurityConfiguration.class })
public class WebSliceSecurityConfiguration {
}
