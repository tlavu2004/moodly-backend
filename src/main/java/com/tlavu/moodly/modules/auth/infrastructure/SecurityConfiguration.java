package com.tlavu.moodly.modules.auth.infrastructure;

import com.tlavu.moodly.shared.application.exception.code.global.GlobalErrorCode;
import com.tlavu.moodly.shared.presentation.dto.error.ApiError;
import com.tlavu.moodly.shared.presentation.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers("/actuator/health", "/internal/cdc/**").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(resourceServer -> resourceServer
						.jwt(Customizer.withDefaults())
						.authenticationEntryPoint((request, response, _) ->
								writeError(response, objectMapper, HttpStatus.UNAUTHORIZED, GlobalErrorCode.UNAUTHORIZED, request.getRequestURI())))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint((request, response, _) ->
								writeError(response, objectMapper, HttpStatus.UNAUTHORIZED, GlobalErrorCode.UNAUTHORIZED, request.getRequestURI()))
						.accessDeniedHandler((request, response, _) ->
								writeError(response, objectMapper, HttpStatus.FORBIDDEN, GlobalErrorCode.FORBIDDEN, request.getRequestURI())));
		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(@Value("${moodly.cors.allowed-origins:}") List<String> allowedOrigins) {
		var configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(allowedOrigins.stream().filter(origin -> !origin.isBlank()).toList());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		var source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	@Bean
	@ConditionalOnProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri")
	JwtDecoder jwtDecoder(
			@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
			@Value("${moodly.auth0.audience}") String audience
	) {
		var decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(issuer), audienceValidator(audience)
		));
		return decoder;
	}

	private OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
		return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(audience)
				? OAuth2TokenValidatorResult.success()
				: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "The token audience is invalid.", null));
	}

	private void writeError(
			HttpServletResponse response,
			ObjectMapper objectMapper,
			HttpStatus status,
			GlobalErrorCode errorCode,
			String path
	) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(new ApiError(
				status.value(), errorCode.getCode(), errorCode.getDefaultMessage(), path, List.of()
		)));
	}
}
