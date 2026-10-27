package com.fmatrestaurant.menu.infrastructure;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

import com.fmatrestaurant.menu.api.IdempotencyFilter;

@Configuration(proxyBeanMethods = false)
public class WebConfiguration {

	/** ETag and 304 responses for the lists, computed from the response body. */
	@Bean
	FilterRegistrationBean<ShallowEtagHeaderFilter> listEtagFilter() {
		FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
				new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
		registration.addUrlPatterns(
				"/api/v1/menu/categories",
				"/api/v1/menu/entries",
				"/api/v1/menu/offers",
				"/api/v1/menu/recipes"
		);
		return registration;
	}

	/** Idempotency-Key of the creations and of PATCH of offers; it chooses its routes itself. */
	@Bean
	FilterRegistrationBean<IdempotencyFilter> idempotencyFilter(IdempotencyRecordRepository records,
			PlatformTransactionManager transactionManager) {
		return new FilterRegistrationBean<>(new IdempotencyFilter(records, transactionManager));
	}
}
