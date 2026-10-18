package com.tlavu.moodly.modules.auth.integration.cloudinary;

import com.tlavu.moodly.modules.auth.integration.dto.ConfirmedAsset;

public interface CloudinaryAssetClient {

	ConfirmedAsset findImage(String publicId);

	void deleteImage(String publicId);
}
