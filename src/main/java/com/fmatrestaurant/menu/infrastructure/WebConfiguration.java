package com.fmatrestaurant.menu.infrastructure;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@Configuration(proxyBeanMethods = false)
public class WebConfiguration {

	/** ETag and 304 responses for the lists, computed from the response body. */
	@Bean
	FilterRegistrationBean<ShallowEtagHeaderFilter> listEtagFilter() {
		FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
				new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
		registration.addUrlPatterns("/api/v1/menu/entries", "/api/v1/menu/offers", "/api/v1/inventory/items");
		return registration;
	}

}
