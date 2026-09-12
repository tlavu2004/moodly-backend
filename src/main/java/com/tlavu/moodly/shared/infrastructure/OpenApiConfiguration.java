package com.tlavu.moodly.shared.infrastructure;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

	private static final String BEARER_AUTH = "bearerAuth";

	@Bean
	OpenAPI moodlyOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Moodly API")
						.version("v1")
						.description("Moodly backend API. Authenticate requests with an Auth0 access token."))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
				.components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")));
	}
}
