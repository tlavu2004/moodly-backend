package com.tlavu.moodly.shared.presentation.advice;

import com.tlavu.moodly.shared.presentation.dto.error.ApiError;
import com.tlavu.moodly.shared.presentation.dto.error.FieldErrorResponse;
import com.tlavu.moodly.shared.presentation.dto.response.ApiResponse;
import com.tlavu.moodly.shared.application.exception.ForbiddenException;
import com.tlavu.moodly.shared.application.exception.ResourceNotFoundException;
import com.tlavu.moodly.shared.application.exception.SearchInfrastructureUnavailableException;
import com.tlavu.moodly.shared.application.exception.code.contract.ErrorCode;
import com.tlavu.moodly.shared.application.exception.code.global.GlobalErrorCode;
import com.tlavu.moodly.modules.auth.application.AvatarException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.MDC;
import com.tlavu.moodly.shared.infrastructure.RequestIdFilter;

@Slf4j
@RestControllerAdvice
@SuppressWarnings("unused") // Spring invokes @ExceptionHandler methods via reflection.
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiResponse<Void>> handleValidation(
			MethodArgumentNotValidException exception,
			HttpServletRequest request
	) {
		var errors = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> new FieldErrorResponse(
						error.getField(),
						Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value")
				))
				.toList();
		return buildErrorResponse(
				HttpStatus.BAD_REQUEST,
				GlobalErrorCode.VALIDATION_FAILED,
				request,
				errors,
				exception
		);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ApiResponse<Void>> handleBadRequest(
			IllegalArgumentException exception,
			HttpServletRequest request
	) {
		return buildErrorResponse(
				HttpStatus.BAD_REQUEST,
				GlobalErrorCode.INVALID_REQUEST,
				safeMessage(exception),
				request,
				List.of(),
				exception
		);
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	ResponseEntity<ApiResponse<Void>> handleMissingHeader(
			MissingRequestHeaderException exception,
			HttpServletRequest request
	) {
		return buildErrorResponse(
				HttpStatus.BAD_REQUEST,
				GlobalErrorCode.MISSING_REQUIRED_HEADER,
				request,
				List.of(),
				exception
		);
	}

	@ExceptionHandler({DuplicateKeyException.class})
	ResponseEntity<ApiResponse<Void>> handleDuplicateKey(
			DuplicateKeyException exception,
			HttpServletRequest request
	) {
		return buildErrorResponse(
				HttpStatus.CONFLICT,
				GlobalErrorCode.DUPLICATE_RESOURCE,
				request,
				List.of(),
				exception
		);
	}

	@ExceptionHandler(AvatarException.class)
	ResponseEntity<ApiResponse<Void>> handleAvatar(AvatarException exception, HttpServletRequest request) {
		logByStatus(HttpStatus.BAD_REQUEST, request, exception);
		var error = new ApiError(400, exception.getCode().getCode(), exception.getMessage(), request.getRequestURI(), List.of(), requestId());
		return ResponseEntity.badRequest().body(ApiResponse.error(error));
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ResponseEntity<ApiResponse<Void>> handleOptimisticLock(
			OptimisticLockingFailureException exception,
			HttpServletRequest request
	) {
		return buildErrorResponse(
				HttpStatus.CONFLICT,
				GlobalErrorCode.CONFLICT,
				"Resource was changed by another request. Refresh and retry.",
				request,
				List.of(),
				exception
		);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiResponse<Void>> handleUnreadableBody(
			HttpMessageNotReadableException exception,
			HttpServletRequest request
	) {
		return buildErrorResponse(
				HttpStatus.BAD_REQUEST,
				GlobalErrorCode.INVALID_REQUEST,
				request,
				List.of(),
				exception
		);
	}

	@ExceptionHandler(ForbiddenException.class)
	ResponseEntity<ApiResponse<Void>> handleForbidden(
			ForbiddenException exception,
			HttpServletRequest request
	) {
		return buildErrorResponse(
				HttpStatus.FORBIDDEN,
				GlobalErrorCode.FORBIDDEN,
				request,
				List.of(),
				exception
		);
	}

	@ExceptionHandler(SearchInfrastructureUnavailableException.class)
	ResponseEntity<ApiResponse<Void>> handleSearchUnavailable(
			SearchInfrastructureUnavailableException exception,
			HttpServletRequest request
	) {
		return buildErrorResponse(
				HttpStatus.SERVICE_UNAVAILABLE,
				GlobalErrorCode.SEARCH_UNAVAILABLE,
				request,
				List.of(),
				exception
		);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	ResponseEntity<ApiResponse<Void>> handleNotFound(
			ResourceNotFoundException exception,
			HttpServletRequest request
	) {
		return buildErrorResponse(
				HttpStatus.NOT_FOUND,
				GlobalErrorCode.NOT_FOUND,
				safeMessage(exception),
				request,
				List.of(),
				exception
		);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception, HttpServletRequest request) {
		return buildErrorResponse(
				HttpStatus.INTERNAL_SERVER_ERROR,
				GlobalErrorCode.INTERNAL_SERVER_ERROR,
				request,
				List.of(),
				exception
		);
	}

	private ResponseEntity<ApiResponse<Void>> buildErrorResponse(
			HttpStatus status,
			ErrorCode errorCode,
			HttpServletRequest request,
			List<FieldErrorResponse> errors,
			Exception exception
	) {
		return buildErrorResponse(status, errorCode, errorCode.getDefaultMessage(), request, errors, exception);
	}

	private ResponseEntity<ApiResponse<Void>> buildErrorResponse(
			HttpStatus status,
			ErrorCode errorCode,
			String message,
			HttpServletRequest request,
			List<FieldErrorResponse> errors,
			Exception exception
	) {
		logByStatus(status, request, exception);
		var error = new ApiError(status.value(), errorCode.getCode(), message, request.getRequestURI(), errors, requestId());
		return ResponseEntity.status(status).body(ApiResponse.error(error));
	}

	private void logByStatus(HttpStatus status, HttpServletRequest request, Throwable exception) {
		if (status.is5xxServerError()) {
			log.error(
					"Unexpected request failure at {} ({})",
					request.getRequestURI(),
					exception.getClass().getSimpleName()
			);
		} else {
			log.warn(
					"Request rejected at {} ({})",
					request.getRequestURI(),
					exception.getClass().getSimpleName()
			);
		}
	}

	private String requestId() {
		return java.util.Objects.requireNonNullElse(MDC.get(RequestIdFilter.MDC_KEY), "unavailable");
	}

	private String safeMessage(Throwable exception) {
		if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
			return "Unexpected error";
		}
		return exception.getMessage();
	}
}
