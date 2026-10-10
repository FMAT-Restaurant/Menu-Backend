package com.fmatrestaurant.menu.api;

import java.util.List;
import java.util.Map;

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

/**
 * Translates errors into the error envelopes of the API contract.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	private static final String NOT_FOUND = "No se encontró el recurso solicitado.";

	@ExceptionHandler({ CatalogEntryNotFoundException.class, ImageNotFoundException.class })
	ResponseEntity<ErrorEnvelope> notFound() {
		return error(HttpStatus.NOT_FOUND, NOT_FOUND);
	}

	@ExceptionHandler(InvalidFieldException.class)
	ResponseEntity<ErrorEnvelope> invalidField(InvalidFieldException e) {
		return invalid(e.getPath(), e.getMessage());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ErrorEnvelope> unreadableBody() {
		return invalid("/", "The request body is missing, is not valid JSON or has values of the wrong type");
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
		return error(HttpStatus.PRECONDITION_FAILED, "El recurso cambió desde que fue leído.");
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	ResponseEntity<ErrorEnvelope> missingHeader(MissingRequestHeaderException e) {
		return error(HttpStatus.PRECONDITION_REQUIRED, "Se requiere el encabezado " + e.getHeaderName() + ".");
	}

	private static ResponseEntity<ErrorEnvelope> invalid(String path, String message) {
		ErrorBody body = new ErrorBody("Revisa los datos enviados en la solicitud.",
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
