package com.tlavu.moodly.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;

@TestConfiguration(proxyBeanMethods = false)
public class MongoTestConfiguration {

	@Bean
	JwtDecoder testJwtDecoder() {
		return _ -> {
			throw new BadJwtException("Real bearer tokens are not decoded in integration tests.");
		};
	}

}
