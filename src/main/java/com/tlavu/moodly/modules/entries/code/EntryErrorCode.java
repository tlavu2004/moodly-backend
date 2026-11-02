package com.tlavu.moodly.modules.entries.code;

import com.tlavu.moodly.shared.error.code.contract.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum EntryErrorCode implements ErrorCode {
	ENTRY_INVALID_RANGE("ENTRY_INVALID_RANGE", "The entry range is invalid.", HttpStatus.BAD_REQUEST);

	private final String code;
	private final String defaultMessage;
	private final HttpStatus status;
}
