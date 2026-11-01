package com.tlavu.moodly.modules.auth.exception;

/** A failed obsolete-asset deletion is queued for retry after profile persistence. */
public final class AvatarAssetDeletionException extends IllegalStateException {
	public AvatarAssetDeletionException(String message) {
		super(message);
	}

	public AvatarAssetDeletionException(String message, Throwable cause) {
		super(message, cause);
	}
}
