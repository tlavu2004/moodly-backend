package com.tlavu.moodly.modules.auth.integration.cloudinary;

import com.tlavu.moodly.modules.auth.exception.AssetSignatureException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class CloudinarySignature {
	private CloudinarySignature() {}

	public static String sign(String parameters, String apiSecret) {
		try {
			byte[] input = (parameters + apiSecret).getBytes(StandardCharsets.UTF_8);
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
		} catch (NoSuchAlgorithmException exception) {
			throw new AssetSignatureException("SHA-256 is unavailable for asset signing", exception);
		}
	}
}
