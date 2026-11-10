package com.fmatrestaurant.menu.infrastructure;

import com.fmatrestaurant.menu.infrastructure.repository.IdempotencyRecordRepository;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

import com.fmatrestaurant.menu.api.ApiVersioning;
import com.fmatrestaurant.menu.api.IdempotencyFilter;

@Configuration(proxyBeanMethods = false)
public class WebConfiguration {

	/** ETag and 304 responses for the lists, computed from the response body. */
	@Bean
	FilterRegistrationBean<ShallowEtagHeaderFilter> listEtagFilter() {
		FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
				new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
		registration.addUrlPatterns(
				ApiVersioning.BASE + "/menu/categories",
				ApiVersioning.BASE + "/menu/entries",
				ApiVersioning.BASE + "/menu/offers",
				ApiVersioning.BASE + "/menu/recipes"
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
