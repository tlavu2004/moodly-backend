package com.tlavu.moodly.modules.e2e.code;

import com.tlavu.moodly.shared.error.code.contract.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum E2eErrorCode implements ErrorCode {
	E2E_RESET_FORBIDDEN("E2E_RESET_FORBIDDEN", "You are not allowed to perform this operation.", HttpStatus.FORBIDDEN);

	private final String code;
	private final String defaultMessage;
	private final HttpStatus status;
}
