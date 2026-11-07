package dev.structiq.common;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springdoc.core.customizers.OpenApiCustomizer;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;

@Configuration
@OpenAPIDefinition(info=@Info(title="StructIQ API", version="0.5.0",
    description="Project membership is checked against the database on every request. Nonmembers receive 404. Project PATCH nulls retain existing values; task PATCH null assigneeId/dueDate clears them."),
    security=@SecurityRequirement(name="bearerAuth"))
@SecurityScheme(name="bearerAuth", type=SecuritySchemeType.HTTP, scheme="bearer", bearerFormat="JWT")
public class OpenApiConfig {
    @Bean OpenApiCustomizer errorResponses() {
        return api -> api.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
            operation.getResponses().addApiResponse("400", error("INVALID_INPUT", "Check the supplied fields", "Invalid input or assignee"));
            operation.getResponses().addApiResponse("401", error("UNAUTHENTICATED", "Authentication required", "Missing/invalid bearer token or credentials"));
            operation.getResponses().addApiResponse("403", error("FORBIDDEN", "This operation requires a project manager", "Role does not permit this operation"));
            operation.getResponses().addApiResponse("404", error("NOT_FOUND", "Resource not found", "Resource missing or caller is not a project member"));
            operation.getResponses().addApiResponse("409", error("CONFLICT", "The operation conflicts with existing data", "Membership or data conflict"));
            if ("upload".equals(operation.getOperationId()))
                operation.getResponses().addApiResponse("413",error("FILE_TOO_LARGE","The file exceeds the configured upload limit","Upload too large"));
            operation.getResponses().addApiResponse("503",error("STORAGE_UNAVAILABLE","Private storage is unavailable","Required service or storage unavailable"));
            if ("login".equals(operation.getOperationId()))
                operation.getResponses().addApiResponse("429", error("RATE_LIMITED", "Too many login attempts; try again in a minute", "Login limit exceeded; Retry-After: 60"));
        }));
    }
    private ApiResponse error(String code,String message,String description) {
        var schema = new ObjectSchema().addProperty("code", new StringSchema())
            .addProperty("message", new StringSchema()).addProperty("correlationId", new StringSchema());
        var content = new Content().addMediaType("application/json", new MediaType().schema(schema)
            .example(java.util.Map.of("code",code,"message",message,"correlationId","example-request-id")));
        return new ApiResponse().description(description).content(content);
    }
}
