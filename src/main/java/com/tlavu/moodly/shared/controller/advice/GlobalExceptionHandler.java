package com.tlavu.moodly.shared.controller.advice;

import com.tlavu.moodly.shared.controller.dto.error.ApiError;
import com.tlavu.moodly.shared.controller.dto.error.FieldErrorResponse;
import com.tlavu.moodly.shared.controller.dto.response.ApiResponse;
import com.tlavu.moodly.shared.error.code.contract.ErrorCode;
import com.tlavu.moodly.shared.error.ExceptionLogSummary;
import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;
import com.tlavu.moodly.shared.error.exception.base.ApplicationException;
import com.tlavu.moodly.shared.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
@SuppressWarnings("unused") // Spring invokes @ExceptionHandler methods via reflection.
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiResponse<Void>> handleValidation(
			MethodArgumentNotValidException exception,
			HttpServletRequest request
	) {
		List<FieldErrorResponse> errors = exception.getBindingResult()
				.getFieldErrors()
				.stream()
				.map(error -> new FieldErrorResponse(
						error.getField(),
						Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value")
				))
				.toList();

		return buildErrorResponse(
				GlobalErrorCode.VALIDATION_FAILED,
				request,
				errors,
				exception
		);
	}

	@ExceptionHandler(ApplicationException.class)
	ResponseEntity<ApiResponse<Void>> handleApplication(
			ApplicationException exception,
			HttpServletRequest request
	) {
		return buildErrorResponse(
				exception.getCode(),
				exception.getMessage(),
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
				GlobalErrorCode.MISSING_REQUIRED_HEADER,
				request,
				List.of(),
				exception
		);
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ResponseEntity<ApiResponse<Void>> handleOptimisticLock(
			OptimisticLockingFailureException exception,
			HttpServletRequest request
	) {
		return buildErrorResponse(
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
				GlobalErrorCode.INVALID_REQUEST,
				request,
				List.of(),
				exception
		);
	}

	@ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
	ResponseEntity<ApiResponse<Void>> handleInvalidParameter(
			Exception exception,
			HttpServletRequest request
	) {
		return buildErrorResponse(
				GlobalErrorCode.INVALID_REQUEST,
				request,
				List.of(),
				exception
		);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
			Exception exception,
			HttpServletRequest request
	) {
		return buildErrorResponse(
				GlobalErrorCode.METHOD_NOT_ALLOWED,
				request,
				List.of(),
				exception
		);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(
			Exception exception,
			HttpServletRequest request
	) {
		return buildErrorResponse(
				GlobalErrorCode.UNSUPPORTED_MEDIA_TYPE,
				request,
				List.of(),
				exception
		);
	}

	@ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
	ResponseEntity<ApiResponse<Void>> handleNotAcceptable(
			Exception exception,
			HttpServletRequest request
	) {
		return buildErrorResponse(
				GlobalErrorCode.NOT_ACCEPTABLE,
				request,
				List.of(),
				exception
		);
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ApiResponse<Void>> handleAuthenticationFailure(
			Exception exception,
			HttpServletRequest request
	) {
		return buildErrorResponse(
				GlobalErrorCode.UNAUTHORIZED,
				request,
				List.of(),
				exception
		);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ApiResponse<Void>> handleAccessDenied(
			Exception exception,
			HttpServletRequest request
	) {
		return buildErrorResponse(
				GlobalErrorCode.FORBIDDEN,
				request,
				List.of(),
				exception
		);
	}

	@ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
	ResponseEntity<ApiResponse<Void>> handleMissingEndpoint(
			Exception exception,
			HttpServletRequest request
	) {
		return buildErrorResponse(
				GlobalErrorCode.NOT_FOUND,
				request,
				List.of(),
				exception
		);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
			Exception exception,
			HttpServletRequest request
	) {
		return buildErrorResponse(
				GlobalErrorCode.INTERNAL_SERVER_ERROR,
				request,
				List.of(),
				exception
		);
	}

	private ResponseEntity<ApiResponse<Void>> buildErrorResponse(
			ErrorCode errorCode,
			HttpServletRequest request,
			List<FieldErrorResponse> errors,
			Exception exception
	) {
		return buildErrorResponse(
				errorCode,
				errorCode.getDefaultMessage(),
				request,
				errors,
				exception
		);
	}

	private ResponseEntity<ApiResponse<Void>> buildErrorResponse(
			ErrorCode errorCode,
			String message,
			HttpServletRequest request,
			List<FieldErrorResponse> errors,
			Exception exception
	) {
		HttpStatus status = errorCode.getStatus();
		logByStatus(
				errorCode,
				request,
				exception
		);

		ApiError error = new ApiError(
				status.value(),
				errorCode.getCode(),
				message,
				request.getRequestURI(),
				errors,
				requestId()
		);

		return ResponseEntity.status(status)
				.contentType(MediaType.APPLICATION_JSON)
				.body(ApiResponse.error(error));
	}

	private void logByStatus(ErrorCode code, HttpServletRequest request, Throwable exception) {
		if (code.getStatus().is5xxServerError()) {
			log.error(
					"Unexpected request failure at {}: method={}, status={}, code={}, cause={}",
					request.getRequestURI(),
					request.getMethod(),
					code.getStatus().value(),
					code.getCode(),
					ExceptionLogSummary.of(exception)
			);
		} else {
			log.warn(
					"Request rejected at {}: method={}, status={}, code={}, exception={}",
					request.getRequestURI(),
					request.getMethod(),
					code.getStatus().value(),
					code.getCode(),
					exception.getClass().getSimpleName()
			);
		}
	}

	private String requestId() {
		return Objects.requireNonNullElse(
				MDC.get(RequestIdFilter.MDC_KEY),
				"unavailable"
		);
	}

}
