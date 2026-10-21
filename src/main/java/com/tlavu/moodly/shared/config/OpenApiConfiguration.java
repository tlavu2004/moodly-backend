package com.tlavu.moodly.shared.config;

import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;

@Configuration
public class OpenApiConfiguration {

	private static final String BEARER_AUTH = "bearerAuth";
	private static final String MAINTENANCE_KEY = "maintenanceKey";

	@Bean
	OpenAPI moodlyOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Moodly API")
						.version("v1")
						.description("Moodly backend API. Authenticate requests with an Auth0 access token. Calendar dates use Asia/Ho_Chi_Minh; weeks start on Monday."))
				.addServersItem(
						new Server().url("http://localhost:8080")
								.description("Local development server")
				)
				.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
				.components(
						new Components().addSecuritySchemes(
								BEARER_AUTH,
								new SecurityScheme().type(SecurityScheme.Type.HTTP)
										.scheme("bearer")
										.bearerFormat("JWT")
										.description("Auth0 access token.")
						)
								.addSecuritySchemes(
										MAINTENANCE_KEY,
										new SecurityScheme().type(SecurityScheme.Type.APIKEY)
												.in(SecurityScheme.In.HEADER)
												.name("X-Maintenance-Key")
												.description("Internal maintenance credential. This is not an Auth0 bearer token.")
								)
				);
	}

	@Bean
	OpenApiCustomizer standardErrorResponses() {
		return openApi -> {
			Components components = openApi.getComponents();
			components.addSchemas("ApiResponseApiError", errorEnvelopeSchema());
			addEnvelopeExamples(components);
			addReusableErrorResponses(components);
			addDefaultErrorResponses(openApi);
			addConflictResponses(openApi);
			addUnavailableResponses(openApi);
		};
	}

	private void addEnvelopeExamples(Components components) {
		components.addExamples(
				"SuccessEnvelope",
				new Example().summary("Successful response")
						.value(
								orderedMap(
										"success",
										true,
										"data",
										orderedMap("id", "example-id"),
										"timestamp", "2026-09-12T10:00:00Z"
								)
						)
		);
		components.addExamples(
				"EmptyEnvelope",
				new Example().summary("Successful structured empty response")
						.value(
								orderedMap(
										"success",
										true,
										"data",
										orderedMap("items", List.of()),
										"timestamp", "2026-09-12T10:00:00Z"
								)
						)
		);
		components.addExamples(
				"ErrorEnvelope",
				new Example().summary("Error response")
						.value(
								orderedMap(
										"success",
										false,
										"error",
										orderedMap(
												"status",
												GlobalErrorCode.INVALID_REQUEST.getStatus().value(),
												"code",
												GlobalErrorCode.INVALID_REQUEST.getCode(),
												"message",
												GlobalErrorCode.INVALID_REQUEST.getDefaultMessage(),
												"path",
												"/example",
												"errors",
												List.of(),
												"requestId",
												"2ea18f35-e92e-4ed0-a629-c3f2fbffc45d"
										),
										"timestamp",
										"2026-09-12T10:00:00Z"
								)
						)
		);
	}

	private void addReusableErrorResponses(Components components) {
		components.addResponses(
				"BadRequest",
				errorResponse(
						"The request is invalid.",
						GlobalErrorCode.VALIDATION_FAILED,
						"One or more fields are invalid."
				)
		);
		components.addResponses(
				"Unauthorized",
				errorResponse(
						"Authentication is required or the access token is invalid.",
						GlobalErrorCode.UNAUTHORIZED,
						"Authentication is required."
				)
		);
		components.addResponses(
				"Forbidden",
				errorResponse(
						"The authenticated user is not allowed to perform this operation.",
						GlobalErrorCode.FORBIDDEN,
						"Access is denied."
				)
		);
		components.addResponses(
				"MaintenanceKeyRequired",
				errorResponse("A valid X-Maintenance-Key header is required.",
						GlobalErrorCode.FORBIDDEN,
						"You are not allowed to perform this operation."
				)
		);
		components.addResponses(
				"NotFound",
				errorResponse(
						"The requested resource was not found.",
						GlobalErrorCode.NOT_FOUND,
						"The requested resource was not found."
				)
		);
		components.addResponses(
				"Conflict",
				errorResponse(
						"The resource conflicts with its current state.",
						GlobalErrorCode.CONFLICT,
						"Refresh the resource and retry."
				)
		);
		components.addResponses(
				"AvatarUploadNotFound",
				errorResponse(
						"The avatar upload is unknown, expired, or belongs to another user.",
						GlobalErrorCode.AVATAR_UPLOAD_NOT_FOUND,
						"The avatar upload was not found or has expired."
				)
		);
		components.addResponses(
				"InternalServerError",
				errorResponse(
						"An unexpected server error occurred.",
						GlobalErrorCode.INTERNAL_SERVER_ERROR,
						"An unexpected error occurred."
				)
		);
		components.addResponses("MethodNotAllowed", errorResponse(
				"The HTTP method is not supported for this endpoint.", GlobalErrorCode.METHOD_NOT_ALLOWED,
				"The HTTP method is not supported for this endpoint."));
		components.addResponses("UnsupportedMediaType", errorResponse(
				"The request content type is not supported.", GlobalErrorCode.UNSUPPORTED_MEDIA_TYPE,
				"The request content type is not supported."));
		components.addResponses("NotAcceptable", errorResponse(
				"The requested response content type is not supported.", GlobalErrorCode.NOT_ACCEPTABLE,
				"The requested response content type is not supported."));
		components.addResponses("SearchUnavailable", errorResponse(
				"Search is temporarily unavailable.", GlobalErrorCode.SEARCH_UNAVAILABLE,
				"Search is temporarily unavailable."));
		components.addResponses("AvatarServiceUnavailable", errorResponse(
				"Avatar service is temporarily unavailable.", GlobalErrorCode.AVATAR_SERVICE_UNAVAILABLE,
				"Avatar service is temporarily unavailable."));
	}

	private void addDefaultErrorResponses(OpenAPI openApi) {
		openApi.getPaths()
				.values()
				.forEach(pathItem -> pathItem.readOperations()
						.forEach(operation -> {
							ApiResponses responses = operation.getResponses();
							responses.addApiResponse("400", reference("BadRequest"));
							responses.addApiResponse("405", reference("MethodNotAllowed"));
							responses.addApiResponse("406", reference("NotAcceptable"));
							responses.addApiResponse("415", reference("UnsupportedMediaType"));

							if (usesMaintenanceKey(operation)) {
								responses.addApiResponse("403", reference("MaintenanceKeyRequired"));
							} else {
								responses.addApiResponse("401", reference("Unauthorized"));
								responses.addApiResponse("403", reference("Forbidden"));
							}

							responses.addApiResponse("500", reference("InternalServerError"));
						})
				);
	}

	private void addConflictResponses(OpenAPI openApi) {
		PathItem updateHabit = openApi.getPaths().get("/habits/{habitId}");

		if (updateHabit != null && updateHabit.getPatch() != null) {
			addConflictResponse(updateHabit.getPatch());
		}

		PathItem archiveHabit = openApi.getPaths().get("/habits/{habitId}/archive");

		if (archiveHabit != null && archiveHabit.getPost() != null) {
			addConflictResponse(archiveHabit.getPost());
		}

		PathItem restoreHabit = openApi.getPaths().get("/habits/{habitId}/restore");

		if (restoreHabit != null && restoreHabit.getPost() != null) {
			addConflictResponse(restoreHabit.getPost());
		}
	}

	private void addConflictResponse(Operation operation) {
		operation.getResponses().addApiResponse("409", reference("Conflict"));
	}

	private void addUnavailableResponses(OpenAPI openApi) {
		openApi.getPaths().forEach((path, item) -> {
			if (path.startsWith("/entries/search") || path.startsWith("/internal/cdc") || path.startsWith("/internal/e2e")) {
				item.readOperations().forEach(operation -> operation.getResponses().addApiResponse("503", reference("SearchUnavailable")));
			} else if (path.startsWith("/me/avatar")) {
				item.readOperations().forEach(operation -> operation.getResponses().addApiResponse("503", reference("AvatarServiceUnavailable")));
			}
		});
	}

	private boolean usesMaintenanceKey(Operation operation) {
		return operation.getSecurity() != null &&
				operation.getSecurity()
						.stream()
						.anyMatch(requirement -> requirement.containsKey(MAINTENANCE_KEY));
	}

	private Schema<?> errorEnvelopeSchema() {
		return new ObjectSchema()
				.required(List.of("success", "error", "timestamp"))
				.addProperty("success", new BooleanSchema().example(false))
				.addProperty("data", new ObjectSchema().nullable(true))
				.addProperty("error", new Schema<>().$ref("#/components/schemas/ApiError"))
				.addProperty("timestamp", new DateTimeSchema().example("2026-09-12T10:00:00Z"));
	}

	private ApiResponse errorResponse(
			String description,
			GlobalErrorCode code,
			String message
	) {
		Example example = new Example().value(
				orderedMap(
						"success", false,
						"data", null,
						"error", orderedMap(
								"status", code.getStatus().value(),
								"code", code.getCode(),
								"message", message,
								"path", "/example",
								"errors", List.of(),
								"requestId", "2ea18f35-e92e-4ed0-a629-c3f2fbffc45d"
						),
						"timestamp", "2026-09-12T10:00:00Z"
				)
		);

		return new ApiResponse().description(description)
				.content(
						new Content().addMediaType(
								"application/json",
								new MediaType().schema(
										new Schema<>().$ref("#/components/schemas/ApiResponseApiError")
								)
								.addExamples("default", example)
						)
				);
	}

	private ApiResponse reference(String name) {
		return new ApiResponse().$ref("#/components/responses/" + name);
	}

	private Map<String, Object> orderedMap(Object... keyValues) {
		Map<String, Object> values = new LinkedHashMap<>();

		for (int index = 0; index < keyValues.length; index += 2) {
			values.put((String) keyValues[index], keyValues[index + 1]);
		}

		return values;
	}
}
