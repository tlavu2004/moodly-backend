package com.tlavu.moodly.shared.error.code.contract;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

	String getCode();

	String getDefaultMessage();

	HttpStatus getStatus();
}
