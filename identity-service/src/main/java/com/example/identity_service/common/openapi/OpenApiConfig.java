package com.example.identity_service.common.openapi;

import com.example.identity_service.common.error.ErrorCode;
import com.example.identity_service.common.error.ErrorResponse;
import com.example.identity_service.internal.InternalController;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The OpenAPI document served at {@code /v3/api-docs} (and Swagger UI at {@code /swagger-ui.html}). Endpoints only
 * carry summaries; who may call them is read from their {@code @PreAuthorize}, so the documented permission,
 * security requirement and 401/403 responses can't drift from the code.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	static final String BEARER = "bearerAuth";

	static final String SERVICE_TOKEN = "serviceToken";

	private static final String ERROR_SCHEMA = "#/components/schemas/ErrorResponse";

	private static final Pattern QUOTED = Pattern.compile("'([^']+)'");

	@Bean
	OpenAPI identityOpenApi() {
		Components components = new Components()
				.addSecuritySchemes(BEARER, new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")
						.bearerFormat("JWT")
						.description("User access token from POST /api/v1/identity/auth/login (RS256, 15 minutes). "
								+ "Refresh it with POST /api/v1/identity/auth/refresh."))
				.addSecuritySchemes(SERVICE_TOKEN, new SecurityScheme().type(SecurityScheme.Type.APIKEY)
						.in(SecurityScheme.In.HEADER).name("X-Service-Token")
						.description("Per-service secret for /internal/v1/identity (IDENTITY_INTERNAL_TOKEN_<SERVICE>)."));
		ResolvedSchema error = ModelConverters.getInstance()
				.resolveAsResolvedSchema(new AnnotatedType(ErrorResponse.class));
		components.addSchemas("ErrorResponse", error.schema);
		error.referencedSchemas.forEach(components::addSchemas);
		return new OpenAPI()
				.info(new Info().title("Identity & Seller API").version("v1").description("""
						Identity service of the e-commerce MVP: accounts, sign-in, customer and seller profiles, \
						address book, regions, public seller lookup, admin review and moderation, and the internal \
						API for other services.

						Every success is `{ "data": ... }` (lists add `meta`); every error is \
						`{ "error": { code, message, details, requestId } }` with a stable `code` from API §4.1. \
						Send `X-Request-Id` to get it back in responses and errors."""))
				.servers(List.of(new Server().url("http://localhost:8083").description("identity-service (dev)")))
				.components(components);
	}

	/** Permission text, security requirement and common error responses from each endpoint's {@code @PreAuthorize}. */
	@Bean
	OperationCustomizer permissionsFromPreAuthorize() {
		return (operation, handler) -> {
			PreAuthorize rule = preAuthorize(handler);
			boolean internal = InternalController.class.isAssignableFrom(handler.getBeanType());
			if (internal) {
				SecurityRequirement requirement = new SecurityRequirement().addList(SERVICE_TOKEN);
				if (takesAuthorizationHeader(handler)) {
					requirement.addList(BEARER);
				}
				operation.setSecurity(List.of(requirement));
				appendDescription(operation, "**Callers:** " + quoted(rule).stream()
						.map(authority -> "`" + authority.replace("service:", "") + "`")
						.collect(Collectors.joining(", ")));
			}
			else if (rule != null) {
				operation.setSecurity(List.of(new SecurityRequirement().addList(BEARER)));
				List<String> permissions = quoted(rule);
				appendDescription(operation, permissions.isEmpty() ? "**Permission:** any signed-in user"
						: "**Permission:** `" + String.join("`, `", permissions) + "`");
			}
			else {
				operation.setSecurity(List.of());
			}
			addErrors(operation, handler, internal || rule != null);
			return operation;
		};
	}

	/**
	 * One response per HTTP status listing its error codes: VALIDATION_ERROR when the endpoint takes input, the
	 * authentication codes when it is secured, and the endpoint's {@link ErrorCodes}.
	 */
	private static void addErrors(Operation operation, HandlerMethod handler, boolean secured) {
		Set<ErrorCode> codes = new LinkedHashSet<>();
		boolean hasParameters = operation.getParameters() != null && !operation.getParameters().isEmpty();
		if (operation.getRequestBody() != null || hasParameters) {
			codes.add(ErrorCode.VALIDATION_ERROR);
		}
		if (secured) {
			codes.addAll(List.of(ErrorCode.UNAUTHENTICATED, ErrorCode.TOKEN_EXPIRED, ErrorCode.TOKEN_STALE,
					ErrorCode.FORBIDDEN));
		}
		ErrorCodes documented = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), ErrorCodes.class);
		if (documented != null) {
			codes.addAll(List.of(documented.value()));
		}
		Map<Integer, List<ErrorCode>> byStatus = new TreeMap<>(codes.stream()
				.collect(Collectors.groupingBy(code -> code.status().value())));
		ApiResponses responses = operation.getResponses() == null ? new ApiResponses() : operation.getResponses();
		byStatus.forEach((status, group) -> responses.put(String.valueOf(status), error(group.stream()
				.map(code -> "- `" + code.name() + "`: " + code.defaultMessage())
				.collect(Collectors.joining("\n")))));
		operation.setResponses(responses);
	}

	private static ApiResponse error(String description) {
		return new ApiResponse().description(description).content(new Content().addMediaType(
				org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
				new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA))));
	}

	private static PreAuthorize preAuthorize(HandlerMethod handler) {
		PreAuthorize onMethod = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), PreAuthorize.class);
		return onMethod != null ? onMethod
				: AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), PreAuthorize.class);
	}

	private static boolean takesAuthorizationHeader(HandlerMethod handler) {
		return Arrays.stream(handler.getMethodParameters())
				.map(parameter -> parameter.getParameterAnnotation(RequestHeader.class))
				.anyMatch(header -> header != null && HttpHeaders.AUTHORIZATION.equalsIgnoreCase(header.name()));
	}

	private static List<String> quoted(PreAuthorize rule) {
		List<String> values = new ArrayList<>();
		if (rule != null) {
			Matcher matcher = QUOTED.matcher(rule.value());
			while (matcher.find()) {
				values.add(matcher.group(1));
			}
		}
		return values;
	}

	private static void appendDescription(Operation operation, String text) {
		String current = operation.getDescription();
		operation.setDescription(current == null || current.isBlank() ? text : current + "\n\n" + text);
	}

}
