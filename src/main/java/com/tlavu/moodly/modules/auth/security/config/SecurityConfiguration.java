package com.tlavu.moodly.modules.auth.security.config;

import com.tlavu.moodly.shared.controller.dto.error.ApiError;
import com.tlavu.moodly.shared.controller.dto.response.ApiResponse;
import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;
import com.tlavu.moodly.shared.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import com.tlavu.moodly.shared.error.exception.common.ErrorResponseWriteException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import static java.util.Objects.requireNonNullElse;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {
	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			ObjectMapper objectMapper,
			Environment environment,
			@Value("${moodly.cdc.admin-authority:SCOPE_moodly:admin}") String cdcAdminAuthority,
			@Value("${moodly.rate-limit.enabled:false}") boolean rateLimitEnabled,
			@Value("${moodly.rate-limit.requests:120}") int rateLimitRequests,
			@Value("${moodly.rate-limit.window:60s}") Duration rateLimitWindow,
			@Value("${moodly.rate-limit.max-subjects:10000}") int rateLimitMaxSubjects
	) {
		boolean production = environment.acceptsProfiles(
			Profiles.of("prod", "production")
		);
		http.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers(
								"/actuator/health",
								"/internal/e2e/**",
								"/swagger-ui.html",
								"/swagger-ui/**",
								"/v3/api-docs/**"
						).permitAll()
						.requestMatchers("/internal/cdc/**")
								.access(
										(authentication, context) -> new AuthorizationDecision(
												!production ||
												(
													Objects.requireNonNull(authentication.get()).isAuthenticated() &&
													Objects.requireNonNull(authentication.get()).getAuthorities().stream()
															.anyMatch(authority -> Objects.equals(
																	authority.getAuthority(),
																	cdcAdminAuthority
															))
												)
										)
								)
						.anyRequest()
						.authenticated()
				)
				.oauth2ResourceServer(resourceServer -> resourceServer
						.jwt(Customizer.withDefaults())
						.authenticationEntryPoint((
								request,
								response,
								_
						) -> writeError(
								response,
								objectMapper,
								GlobalErrorCode.UNAUTHORIZED,
								request.getRequestURI()
						))
				)
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint((
								request,
								response,
								_
						) -> writeError(
								response,
								objectMapper,
								GlobalErrorCode.UNAUTHORIZED,
								request.getRequestURI()
						))
						.accessDeniedHandler((
								request,
								response,
								_
						) -> writeError(
								response,
								objectMapper,
								GlobalErrorCode.FORBIDDEN,
								request.getRequestURI()
						))
				);
		if (rateLimitEnabled) {
			RequestQuota quota = new RequestQuota(
					rateLimitRequests,
					rateLimitMaxSubjects,
					rateLimitWindow,
					System::nanoTime
			);
			http.addFilterAfter(
					new RequestQuotaFilter(quota, objectMapper),
					AuthorizationFilter.class
			);
		}

		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(
			@Value("${moodly.cors.allowed-origins:}") List<String> allowedOrigins
	) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(
				allowedOrigins.stream()
						.filter(origin -> !origin.isBlank())
						.toList()
		);
		configuration.setAllowedMethods(
				List.of(
						"GET",
						"POST",
						"PUT",
						"PATCH",
						"DELETE",
						"OPTIONS"
				)
		);
		configuration.setAllowedHeaders(
				List.of(
						"Authorization",
						"Content-Type",
						RequestIdFilter.HEADER
				)
		);
		configuration.setExposedHeaders(List.of(
				RequestIdFilter.HEADER,
				HttpHeaders.RETRY_AFTER
		));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);

		return source;
	}

	@Bean
	@ConditionalOnProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri")
	JwtDecoder jwtDecoder(
			@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
			@Value("${moodly.auth0.audience}") String audience,
			@Value("${moodly.auth0.http.connect-timeout:10s}") Duration connectTimeout,
			@Value("${moodly.auth0.http.read-timeout:10s}") Duration readTimeout,
			@Value("${moodly.auth0.http.max-attempts:3}") int maxAttempts,
			@Value("${moodly.auth0.http.retry-delay:500ms}") Duration retryDelay
	) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer)
				.restOperations(Auth0HttpClient.create(
						connectTimeout,
						readTimeout,
						maxAttempts,
						retryDelay
				)).build();
		decoder.setJwtValidator(
				new DelegatingOAuth2TokenValidator<>(
						JwtValidators.createDefaultWithIssuer(issuer),
						audienceValidator(audience)
				)
		);

		return decoder;
	}

	OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
		return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(audience)
				? OAuth2TokenValidatorResult.success()
				: OAuth2TokenValidatorResult.failure(
						new OAuth2Error(
								"invalid_token",
								"The token audience is invalid.",
								null
						)
				);
	}

	static void writeError(
			HttpServletResponse response,
			ObjectMapper objectMapper,
			GlobalErrorCode errorCode,
			String path
	) {
		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);

		try {
			objectMapper.writeValue(
				response.getOutputStream(),
				ApiResponse.error(
						new ApiError(
								errorCode.getStatus().value(),
								errorCode.getCode(),
								errorCode.getDefaultMessage(),
								path,
								List.of(),
								requireNonNullElse(
										MDC.get(RequestIdFilter.MDC_KEY),
										"unavailable"
								)
						)
				)
			);
		} catch (IOException failure) {
			throw new ErrorResponseWriteException(failure);
		}
	}
}
