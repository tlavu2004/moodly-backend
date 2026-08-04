package com.tlavu.moodly.shared.api;

import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
		var errors = exception.getBindingResult().getFieldErrors().stream()
				.collect(Collectors.toMap(
					error -> error.getField(),
					error -> error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage(),
					(first, ignored) -> first
				));
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed.");
		problem.setProperty("errors", errors);
		return problem;
	}

	@ExceptionHandler({IllegalArgumentException.class, MissingRequestHeaderException.class})
	ProblemDetail handleBadRequest(Exception exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	@ExceptionHandler(DuplicateKeyException.class)
	ProblemDetail handleDuplicateKey() {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "A document with the same unique key already exists.");
	}
}
