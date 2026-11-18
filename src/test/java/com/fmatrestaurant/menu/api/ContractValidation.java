package com.fmatrestaurant.menu.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.MockMvcResponse;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.report.SimpleValidationReportFormat;
import com.atlassian.oai.validator.report.ValidationReport;

/**
 * Validates the responses of the integration tests against the served contract ({@code static/openapi.yaml}).
 * Only the response is checked: the tests send invalid requests on purpose, and authentication is not
 * implemented yet (BE-12, BE-13).
 *
 * <pre>
 * mockMvc.perform(get("/api/v1/menu/categories")).andExpect(conformsToContract());
 * </pre>
 */
public final class ContractValidation {

	/** The contract paths are relative to its server URL. */
	private static final String BASE_PATH = "/api/v1";

	private static final OpenApiInteractionValidator VALIDATOR = OpenApiInteractionValidator
			.createForInlineApiSpecification(servedContract())
			.withBasePathOverride(BASE_PATH)
			.build();

	private ContractValidation() {
	}

	/** Fails when the response is not one the contract declares for the operation of the request. */
	public static ResultMatcher conformsToContract() {
		return result -> {
			ValidationReport report = validate(result);
			if (report.hasErrors()) {
				MockHttpServletRequest request = result.getRequest();
				throw new AssertionError("The response of " + request.getMethod() + " " + request.getRequestURI()
						+ " does not conform to the contract:\n"
						+ SimpleValidationReportFormat.getInstance().apply(report));
			}
		};
	}

	/** The contract violations of the response of a request. */
	public static ValidationReport validate(MvcResult result) {
		MockHttpServletRequest request = result.getRequest();
		return VALIDATOR.validateResponse(request.getRequestURI(), Request.Method.valueOf(request.getMethod()),
				MockMvcResponse.of(result.getResponse()));
	}

	private static String servedContract() {
		try (InputStream contract = ContractValidation.class.getResourceAsStream("/static/openapi.yaml")) {
			if (contract == null) {
				throw new IllegalStateException("static/openapi.yaml is not on the classpath");
			}
			return new String(contract.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
