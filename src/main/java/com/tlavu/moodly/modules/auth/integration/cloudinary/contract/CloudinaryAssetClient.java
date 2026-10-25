package com.tlavu.moodly.modules.auth.integration.cloudinary.contract;

public interface CloudinaryAssetClient {
	ConfirmedAsset findImage(String publicId);

	void deleteImage(String publicId);
}
