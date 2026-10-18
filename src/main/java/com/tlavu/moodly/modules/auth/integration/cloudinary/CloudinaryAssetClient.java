package com.tlavu.moodly.modules.auth.integration.cloudinary;

import com.tlavu.moodly.modules.auth.dto.result.ConfirmedAsset;

public interface CloudinaryAssetClient {

	ConfirmedAsset findImage(String publicId);

	void deleteImage(String publicId);
}
