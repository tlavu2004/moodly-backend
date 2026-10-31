package com.tlavu.moodly.modules.auth.integration.cloudinary.port;

import com.tlavu.moodly.modules.auth.integration.cloudinary.model.ConfirmedAsset;

public interface CloudinaryAssetClient {
	ConfirmedAsset findImage(String publicId);

	void deleteImage(String publicId);
}
