package com.tlavu.moodly.shared.infrastructure;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;

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
				.addServersItem(new Server().url("http://localhost:8080").description("Local development server"))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
				.components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")));
	}

	@Bean
	OpenApiCustomizer standardErrorResponses() {
		return openApi -> {
			var components = openApi.getComponents();
			components.addSchemas("ApiResponseApiError", errorEnvelopeSchema());
			components.addResponses("BadRequest", errorResponse("The request is invalid.", 400, "VALIDATION_FAILED", "One or more fields are invalid."));
			components.addResponses("Unauthorized", errorResponse("Authentication is required or the access token is invalid.", 401, "UNAUTHORIZED", "Authentication is required."));
			components.addResponses("Forbidden", errorResponse("The authenticated user is not allowed to perform this operation.", 403, "FORBIDDEN", "Access is denied."));
			components.addResponses("InternalServerError", errorResponse("An unexpected server error occurred.", 500, "INTERNAL_SERVER_ERROR", "An unexpected error occurred."));

			openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operation -> {
				var responses = operation.getResponses();
				responses.addApiResponse("400", reference("BadRequest"));
				responses.addApiResponse("401", reference("Unauthorized"));
				responses.addApiResponse("403", reference("Forbidden"));
				responses.addApiResponse("500", reference("InternalServerError"));
			}));
		};
	}

	private Schema<?> errorEnvelopeSchema() {
		return new ObjectSchema()
				.addProperty("success", new BooleanSchema().example(false))
				.addProperty("data", new ObjectSchema().nullable(true))
				.addProperty("error", new Schema<>().$ref("#/components/schemas/ApiError"))
				.addProperty("timestamp", new DateTimeSchema().example("2026-09-12T10:00:00Z"));
	}

	private ApiResponse errorResponse(String description, int status, String code, String message) {
		var example = new Example().value("""
				{"success":false,"data":null,"error":{"status":%d,"code":"%s","message":"%s","path":"/example","errors":[]},"timestamp":"2026-09-12T10:00:00Z"}
				""".formatted(status, code, message));
		return new ApiResponse().description(description).content(new Content().addMediaType("application/json",
				new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiResponseApiError"))
						.addExamples("default", example)));
	}

	private ApiResponse reference(String name) {
		return new ApiResponse().$ref("#/components/responses/" + name);
	}
}
