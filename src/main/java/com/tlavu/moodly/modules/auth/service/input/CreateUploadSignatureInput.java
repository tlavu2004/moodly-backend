package com.tlavu.moodly.modules.auth.service.input;

public record CreateUploadSignatureInput(
		String contentType,
		long sizeBytes
) {}
