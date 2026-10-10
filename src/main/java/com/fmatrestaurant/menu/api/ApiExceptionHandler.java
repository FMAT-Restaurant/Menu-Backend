package com.fmatrestaurant.menu.api;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fmatrestaurant.menu.application.CatalogEntryNotFoundException;
import com.fmatrestaurant.menu.application.ImageNotFoundException;
import com.fmatrestaurant.menu.application.StaleCatalogEntryException;
import com.fmatrestaurant.menu.domain.Image;
import com.fmatrestaurant.menu.domain.InvalidFieldException;

import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Translates errors into the error envelopes of the API contract.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	private static final String NOT_FOUND = "The requested resource was not found.";

	@ExceptionHandler({ CatalogEntryNotFoundException.class, ImageNotFoundException.class })
	ResponseEntity<ErrorEnvelope> notFound() {
		return error(HttpStatus.NOT_FOUND, NOT_FOUND);
	}

	@ExceptionHandler(InvalidFieldException.class)
	ResponseEntity<ErrorEnvelope> invalidField(InvalidFieldException e) {
		return invalid(e.getPath(), e.getMessage());
	}

	/** A value of the wrong type names its field; broken or missing JSON keeps the generic message. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ErrorEnvelope> unreadableBody(HttpMessageNotReadableException e) {
		if (e.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
			String path = mismatch.getPath().stream()
					.map(reference -> "/" + (reference.getPropertyName() != null ? reference.getPropertyName()
							: reference.getIndex()))
					.collect(Collectors.joining());
			return invalid(path, wrongTypeMessage(mismatch.getTargetType()));
		}
		return invalid("/", "The request body is missing, is not valid JSON or has values of the wrong type");
	}

	private static String wrongTypeMessage(Class<?> targetType) {
		if (targetType == UUID.class) {
			return "The value must be a valid UUID";
		}
		if (targetType != null && Collection.class.isAssignableFrom(targetType)) {
			return "The value must be an array";
		}
		if (targetType != null && targetType.isEnum()) {
			return "The value must be one of " + Arrays.toString(targetType.getEnumConstants());
		}
		return "The value has the wrong type";
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ErrorEnvelope> typeMismatch(MethodArgumentTypeMismatchException e) {
		if (e.getParameter().hasParameterAnnotation(PathVariable.class)) {
			return error(HttpStatus.NOT_FOUND, NOT_FOUND);
		}
		return invalid(e.getName(), "The value " + e.getValue() + " is not valid");
	}

	@ExceptionHandler(MissingServletRequestPartException.class)
	ResponseEntity<ErrorEnvelope> missingPart(MissingServletRequestPartException e) {
		return invalid("/" + e.getRequestPartName(), "The part " + e.getRequestPartName() + " is required");
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ErrorEnvelope> uploadTooLarge() {
		return invalid("/file", "The image must not exceed " + Image.MAX_BYTES / (1024 * 1024) + " MiB");
	}

	@ExceptionHandler({ StaleCatalogEntryException.class, OptimisticLockingFailureException.class })
	ResponseEntity<ErrorEnvelope> stale() {
		return error(HttpStatus.PRECONDITION_FAILED, "The resource has changed since it was read.");
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	ResponseEntity<ErrorEnvelope> missingHeader(MissingRequestHeaderException e) {
		return error(HttpStatus.PRECONDITION_REQUIRED, "The " + e.getHeaderName() + " header is required.");
	}

	private static ResponseEntity<ErrorEnvelope> invalid(String path, String message) {
		ErrorBody body = new ErrorBody("Check the data sent in the request.",
				Map.of("violations", List.of(new Violation(path, message))));
		return ResponseEntity.unprocessableContent().body(new ErrorEnvelope(body));
	}

	private static ResponseEntity<ErrorEnvelope> error(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(new ErrorEnvelope(new ErrorBody(message, null)));
	}

	record ErrorEnvelope(ErrorBody error) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record ErrorBody(String message, Map<String, Object> details) {
	}

	record Violation(String path, String message) {
	}

}
