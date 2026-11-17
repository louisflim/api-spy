package edu.citu.apispy.controller;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
		return badRequest(ex.getMessage());
	}

	/** Business-rule rejections (forged completion, incomplete devices, completed reset). */
	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
		return badRequest(ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("error", "Invalid request payload.");
		body.put("message", "Invalid request payload.");
		body.put("details", ex.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.toList());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
	}

	@ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
	public ResponseEntity<Map<String, Object>> handleConstraintViolation(Exception ex) {
		return badRequest("Invalid request input.");
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
		if (isPayloadTooLarge(ex)) {
			return tooLarge();
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("error", "Malformed request payload.");
		body.put("message", "Malformed request payload.");
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
		Map<String, Object> body = new LinkedHashMap<>();
		String message = ex.getReason() == null ? ex.getStatusCode().toString() : ex.getReason();
		body.put("error", message);
		body.put("message", message);
		return ResponseEntity.status(ex.getStatusCode()).body(body);
	}

	private static ResponseEntity<Map<String, Object>> badRequest(String message) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("error", message);
		body.put("message", message);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
	}

	private static ResponseEntity<Map<String, Object>> tooLarge() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("error", "Request payload too large.");
		body.put("message", "Request payload too large.");
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
	}

	private static boolean isPayloadTooLarge(Throwable ex) {
		Throwable cursor = ex;
		while (cursor != null) {
			if (cursor instanceof IOException && cursor.getMessage() != null
					&& cursor.getMessage().contains("Request payload too large")) {
				return true;
			}
			cursor = cursor.getCause();
		}
		return false;
	}
}
