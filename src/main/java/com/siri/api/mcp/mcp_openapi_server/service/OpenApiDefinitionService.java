package com.siri.api.mcp.mcp_openapi_server.service;

import com.siri.api.mcp.mcp_openapi_server.config.OpenApiProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Getter
public class OpenApiDefinitionService {

    private final OpenApiProperties openApiProperties;
    private final Map<String, ApiOperation> operationCache = new HashMap<>();

    /**
     * Get the cached OpenAPI operations
     * @return Map of operation ID to ApiOperation
     */
    public Map<String, ApiOperation> getOperationCache() {
        return operationCache;
    }

    @PostConstruct
    public void init() {
        OpenAPIV3Parser parser = new OpenAPIV3Parser();
        for (OpenApiProperties.Document doc : openApiProperties.getDocuments()) {
            OpenAPI openAPI = parser.read(doc.getLocation());
            if (openAPI != null) {
                openAPI.getPaths().forEach((path, pathItem) ->
                    pathItem.readOperationsMap().forEach((httpMethod, operation) -> {
                        if (operation.getOperationId() != null) {
                            ApiOperation apiOperation = new ApiOperation(doc, openAPI, path, httpMethod, operation);
                            operationCache.put(operation.getOperationId(), apiOperation);
                        }
                    })
                );
            }
        }
    }

    public Optional<ApiOperation> getOperation(String operationId) {
        return Optional.ofNullable(operationCache.get(operationId));
    }

    public record ApiOperation(
            OpenApiProperties.Document document,
            OpenAPI openAPI,
            String path,
            PathItem.HttpMethod httpMethod,
            Operation operation
    ) {
    }
}
