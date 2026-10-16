package com.tlavu.moodly.modules.auth.integration;

public interface CloudinaryAssetClient {
	ConfirmedAsset findImage(String publicId);
	void deleteImage(String publicId);

	record ConfirmedAsset(String publicId, long version, String contentType, long sizeBytes) {}
}
