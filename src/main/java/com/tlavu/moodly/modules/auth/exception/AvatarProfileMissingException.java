package com.tlavu.moodly.modules.auth.exception;

/** A synchronized profile disappeared before avatar confirmation could persist it. */
public final class AvatarProfileMissingException extends IllegalStateException {
	public AvatarProfileMissingException() {
		super("Avatar profile disappeared during confirmation.");
	}
}
