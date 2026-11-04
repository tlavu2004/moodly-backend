package com.tlavu.moodly.modules.auth.exception;

import com.tlavu.moodly.shared.error.contract.ProviderHttpStatus;

/** A failed obsolete-asset deletion is queued for retry after profile persistence. */
public final class AvatarAssetDeletionException extends IllegalStateException implements ProviderHttpStatus {
	private final int providerStatus;

	public AvatarAssetDeletionException(String message) {
		super(message);
		this.providerStatus = 0;
	}

	public AvatarAssetDeletionException(String message, Throwable cause) {
		super(message, cause);
		this.providerStatus = 0;
	}

	public AvatarAssetDeletionException(String message, int providerStatus) {
		super(message);
		this.providerStatus = providerStatus;
	}

	@Override
	public int providerHttpStatus() {
		return providerStatus;
	}
}
