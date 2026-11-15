package edu.citu.apispy.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("error", ex.getMessage());
		body.put("message", ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
	}

	/** Business-rule rejections (forged completion, incomplete devices, completed reset). */
	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("error", ex.getMessage());
		body.put("message", ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
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

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
		Map<String, Object> body = new LinkedHashMap<>();
		String message = ex.getReason() == null ? ex.getStatusCode().toString() : ex.getReason();
		body.put("error", message);
		body.put("message", message);
		return ResponseEntity.status(ex.getStatusCode()).body(body);
	}
}
