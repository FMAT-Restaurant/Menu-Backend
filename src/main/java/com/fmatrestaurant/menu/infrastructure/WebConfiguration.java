package com.fmatrestaurant.menu.infrastructure;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@Configuration(proxyBeanMethods = false)
public class WebConfiguration {

	/** ETag and 304 responses for collection reads, computed from their response bodies. */
	@Bean
	FilterRegistrationBean<ShallowEtagHeaderFilter> collectionEtagFilter() {
		FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
				new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
		registration.addUrlPatterns("/api/v1/menu/entries");
		registration.addUrlPatterns("/api/v1/menu/categories");
		return registration;
	}

}
