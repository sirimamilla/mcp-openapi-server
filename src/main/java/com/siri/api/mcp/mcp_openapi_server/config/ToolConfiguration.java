package com.siri.api.mcp.mcp_openapi_server.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siri.api.mcp.mcp_openapi_server.service.ApiClient;
import com.siri.api.mcp.mcp_openapi_server.service.OpenApiDefinitionService;
import io.modelcontextprotocol.server.McpSyncServer;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolCallResultConverter;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

@Slf4j
@Configuration
public class ToolConfiguration {

    private final OpenApiDefinitionService openApiDefinitionService;
    private final ApiClient apiClient;
    private final ConfigurableBeanFactory beanFactory;
    private final ObjectMapper objectMapper;
    private final McpSyncServer mcpServer;

    // Cache for resolved schema references to improve performance
    private final Map<String, Schema<?>> schemaCache = new ConcurrentHashMap<>();
    // Cache for converted schemas to avoid redundant conversion
    private final Map<String, Map<String, Object>> convertedSchemaCache = new ConcurrentHashMap<>();
    // Cache for response schemas to improve performance
    private final Map<String, Map<String, Object>> responseSchemaCache = new ConcurrentHashMap<>();


    public ToolConfiguration(OpenApiDefinitionService openApiDefinitionService, ApiClient apiClient, ConfigurableBeanFactory beanFactory, ObjectMapper objectMapper, McpSyncServer server) {
        this.openApiDefinitionService = openApiDefinitionService;
        this.apiClient = apiClient;
        this.beanFactory = beanFactory;
        this.objectMapper = objectMapper;
        this.mcpServer = server;
    }

    @PostConstruct
    public void registerOpenApiFunctions() {
        openApiDefinitionService.getOperationCache().forEach((operationId, apiOperation) -> {
            registerSingleOperation(operationId, apiOperation);
        });
    }

    public void registerNewOperations(OpenApiProperties.Document document) {
        openApiDefinitionService.getOperationCache().entrySet().stream()
            .filter(entry -> entry.getValue().document().getName().equals(document.getName()))
            .forEach(entry -> {
                String operationId = entry.getKey();
                OpenApiDefinitionService.ApiOperation apiOperation = entry.getValue();
                registerSingleOperation(operationId, apiOperation);
                log.info("Dynamically registered tool: {}", operationId);
            });
    }

    public void registerSingleOperation(String operationId, OpenApiDefinitionService.ApiOperation apiOperation) {
        String beanName = operationId + "Tool";
        
        if (beanFactory.containsSingleton(beanName)) {
            log.warn("Tool with operation ID '{}' already exists, skipping registration", operationId);
            return;
        }
        
        // Create a function that delegates to ApiClient
        BiFunction<Map<String, Object>, ToolContext, Object> function = (jsonInput, context) ->
            apiClient.invoke(operationId, jsonInput);

        // Create the tool metadata and definition
        String description = apiOperation.operation().getSummary() != null ?
                apiOperation.operation().getSummary() :
                "Operation: " + operationId;

        String schema = convertParametersToJsonSchema(apiOperation.operation());

        // Create tool definition
        ToolDefinition definition = ToolDefinition.builder()
                .name(operationId)
                .description(description)
                .inputSchema(schema)
                .build();

        // Create tool callback with simple result converter
        FunctionToolCallback<Map<String, Object>, Object> toolCallback =
            new FunctionToolCallback<>(
                definition,
                null,
                Map.class,
                function,
                new SimpleToolCallResultConverter()
            );

        // Register as singleton bean to be discovered by Spring AI
        beanFactory.registerSingleton(beanName, toolCallback);
        mcpServer.addTool(McpToolUtils.toSyncToolSpecification(toolCallback));
        log.info("Successfully registered tool: {}", operationId);
    }

    /**
     * Simple converter for tool call results
     */
    static class SimpleToolCallResultConverter implements ToolCallResultConverter {
        @Override
        public String convert(Object result, Type type) {
            if (result == null) {
                return "null";
            }
            return result.toString();
        }
    }

    /**
     * Parse JSON response string to object
     */
    private Object parseJsonResponse(String responseString) {
        try {
            if (responseString == null || responseString.trim().isEmpty()) {
                return null;
            }

            // Try to parse as JSON
            return objectMapper.readValue(responseString, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.debug("Response is not valid JSON, returning as string: {}", e.getMessage());
            return responseString;
        }
    }

    /**
     * Get response schema for an operation
     */
    private Map<String, Object> getResponseSchema(String operationId, OpenApiDefinitionService.ApiOperation apiOperation) {
        // Check cache first
        if (responseSchemaCache.containsKey(operationId)) {
            return responseSchemaCache.get(operationId);
        }

        try {
            ApiResponses responses = apiOperation.operation().getResponses();
            if (responses == null) {
                return null;
            }

            // Try to get successful response schema (200, 201, etc.)
            ApiResponse successResponse = responses.get("200");
            if (successResponse == null) {
                successResponse = responses.get("201");
            }
            if (successResponse == null) {
                successResponse = responses.get("default");
            }

            if (successResponse != null && successResponse.getContent() != null) {
                Content content = successResponse.getContent();

                // Try JSON first, then XML, then any other type
                MediaType mediaType = content.get("application/json");
                if (mediaType == null) {
                    mediaType = content.get("application/xml");
                }
                if (mediaType == null && !content.isEmpty()) {
                    mediaType = content.values().iterator().next();
                }

                if (mediaType != null && mediaType.getSchema() != null) {
                    Map<String, Object> schema = convertSchemaToMap(mediaType.getSchema());
                    responseSchemaCache.put(operationId, schema);
                    return schema;
                }
            }

            return null;
        } catch (Exception e) {
            log.warn("Error getting response schema for operation {}: {}", operationId, e.getMessage());
            return null;
        }
    }

    /**
     * Convert a Swagger Schema object to a Map representation that can be serialized to JSON Schema
     */
    private Map<String, Object> convertSchemaToMap(Schema<?> schema) {
        return convertSchemaToMap(schema, new HashSet<>());
    }

    /**
     * Convert a Swagger Schema object to a Map representation with circular reference detection
     * @param schema The schema to convert
     * @param processedRefs Set of references already processed in this branch to detect circular references
     * @return A map representation of the schema
     */
    private Map<String, Object> convertSchemaToMap(Schema<?> schema, Set<String> processedRefs) {
        // Handle null schema case
        if (schema == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("type", "object");
            result.put("description", "No schema available");
            return result;
        }

        // Handle $ref (referenced schemas)
        if (schema.get$ref() != null) {
            String ref = schema.get$ref();

            // Check for circular references
            if (processedRefs.contains(ref)) {
                log.warn("Detected circular reference: {}", ref);
                Map<String, Object> result = new HashMap<>();
                result.put("type", "object");
                result.put("description", "Circular reference: " + ref);
                return result;
            }

            // Check cache first for already converted schemas
            if (convertedSchemaCache.containsKey(ref)) {
                log.debug("Using cached converted schema for: {}", ref);
                return new HashMap<>(convertedSchemaCache.get(ref));
            }

            log.debug("Processing schema reference: {}", ref);

            // Add to processed refs to detect circular references
            processedRefs.add(ref);

            Map<String, Object> result = new HashMap<>();
            result.put("$ref", ref);

            // Try to resolve the reference if possible
            Schema<?> resolvedSchema = resolveReference(ref);
            if (resolvedSchema != null) {
                // Merge properties from resolved schema (with circular ref detection)
                Map<String, Object> resolvedMap = convertSchemaToMap(resolvedSchema, processedRefs);
                result.putAll(resolvedMap);
                log.debug("Successfully resolved reference: {}", ref);

                // Cache the result for future use
                convertedSchemaCache.put(ref, new HashMap<>(result));
            } else {
                // If we can't resolve, at least provide the reference info
                result.put("type", "object");
                result.put("description", "Referenced schema: " + ref);
                log.warn("Unable to resolve schema reference: {}", ref);
            }

            // Remove from processed refs when done with this branch
            processedRefs.remove(ref);
            return result;
        }

        Map<String, Object> result = new HashMap<>();

        // Add basic properties
        if (schema.getType() != null) {
            result.put("type", schema.getType());
        }
        if (schema.getDescription() != null) {
            result.put("description", schema.getDescription());
        }
        if (schema.getFormat() != null) {
            result.put("format", schema.getFormat());
        }
        if (schema.getDefault() != null) {
            result.put("default", schema.getDefault());
        }

        // Handle required properties
        if (schema.getRequired() != null && !schema.getRequired().isEmpty()) {
            result.put("required", schema.getRequired());
        }

        // Handle enums
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            result.put("enum", schema.getEnum());
        }

        // Add numeric constraints
        if (schema.getMinimum() != null) {
            result.put("minimum", schema.getMinimum());
        }
        if (schema.getMaximum() != null) {
            result.put("maximum", schema.getMaximum());
        }
        if (schema.getExclusiveMinimum() != null) {
            result.put("exclusiveMinimum", schema.getExclusiveMinimum());
        }
        if (schema.getExclusiveMaximum() != null) {
            result.put("exclusiveMaximum", schema.getExclusiveMaximum());
        }

        // Add string constraints
        if (schema.getMinLength() != null) {
            result.put("minLength", schema.getMinLength());
        }
        if (schema.getMaxLength() != null) {
            result.put("maxLength", schema.getMaxLength());
        }
        if (schema.getPattern() != null) {
            result.put("pattern", schema.getPattern());
        }

        // Add array constraints
        if (schema.getMinItems() != null) {
            result.put("minItems", schema.getMinItems());
        }
        if (schema.getMaxItems() != null) {
            result.put("maxItems", schema.getMaxItems());
        }
        if (schema.getUniqueItems() != null && schema.getUniqueItems()) {
            result.put("uniqueItems", true);
        }

        // Handle nested properties for objects
        if ("object".equals(schema.getType()) && schema.getProperties() != null) {
            Map<String, Object> nestedProperties = new HashMap<>();
            schema.getProperties().forEach((propName, propSchema) -> {
                nestedProperties.put(propName, convertSchemaToMap((Schema<?>) propSchema, new HashSet<>(processedRefs)));
            });
            result.put("properties", nestedProperties);
        }

        // Handle array items
        if ("array".equals(schema.getType()) && schema.getItems() != null) {
            Schema<?> itemsSchema = schema.getItems();
            Map<String, Object> itemsMap = convertSchemaToMap(itemsSchema, new HashSet<>(processedRefs));

            // Ensure primitive types like strings are properly typed in arrays
            if (itemsSchema.getType() != null &&
                ("string".equals(itemsSchema.getType()) ||
                 "integer".equals(itemsSchema.getType()) ||
                 "number".equals(itemsSchema.getType()) ||
                 "boolean".equals(itemsSchema.getType()))) {
                // For primitive types, ensure the type is explicitly set
                if (!itemsMap.containsKey("type")) {
                    itemsMap.put("type", itemsSchema.getType());
                }
            }

            result.put("items", itemsMap);
        }

        // Add any additional properties
        if (schema.getAdditionalProperties() != null) {
            if (schema.getAdditionalProperties() instanceof Schema) {
                result.put("additionalProperties", convertSchemaToMap((Schema<?>) schema.getAdditionalProperties(), new HashSet<>(processedRefs)));
            } else if (schema.getAdditionalProperties() instanceof Boolean) {
                result.put("additionalProperties", schema.getAdditionalProperties());
            }
        }

        return result;
    }

    /**
     * Attempt to resolve a schema reference
     * @param ref The reference string (e.g., "#/components/schemas/Pet")
     * @return The resolved Schema object, or null if it cannot be resolved
     */
    private Schema<?> resolveReference(String ref) {
        if (ref == null) {
            return null;
        }

        // Check cache first
        if (schemaCache.containsKey(ref)) {
            log.debug("Using cached schema for reference: {}", ref);
            return schemaCache.get(ref);
        }

        try {
            Schema<?> resolvedSchema = null;

            // Handle different reference patterns
            if (ref.startsWith("#/components/schemas/")) {
                // Extract the schema name from the reference
                String schemaName = ref.substring("#/components/schemas/".length());
                resolvedSchema = resolveComponentSchema(schemaName);
            } else if (ref.startsWith("#/components/parameters/")) {
                String paramName = ref.substring("#/components/parameters/".length());
                resolvedSchema = resolveParameterSchema(paramName);
            } else if (ref.contains("#")) {
                // Handle external references with local components
                log.warn("External references with local paths not fully supported: {}", ref);
                // This could be enhanced to handle external file references
                return null;
            } else {
                log.warn("Unsupported reference format: {}", ref);
                return null;
            }

            // Cache result if resolved successfully
            if (resolvedSchema != null) {
                schemaCache.put(ref, resolvedSchema);
            }

            return resolvedSchema;
        } catch (Exception e) {
            log.warn("Error resolving schema reference: " + ref, e);
            return null;
        }
    }

    // Helper method to extract parameter name from reference
    private String paramName(String ref) {
        if (ref != null && ref.startsWith("#/components/parameters/")) {
            String name = ref.substring("#/components/parameters/".length());
            // Convert PascalCase to camelCase for consistency with most parameter naming conventions
            if (!name.isEmpty() && Character.isUpperCase(name.charAt(0))) {
                return Character.toLowerCase(name.charAt(0)) + (name.length() > 1 ? name.substring(1) : "");
            }
            return name;
        }
        return "param";
    }

    /**
     * Resolve a component schema by name
     */
    private Schema<?> resolveComponentSchema(String schemaName) {
        // Try to find the schema in all OpenAPI specs we have loaded
        for (OpenApiDefinitionService.ApiOperation operation : openApiDefinitionService.getOperationCache().values()) {
            io.swagger.v3.oas.models.OpenAPI openAPI = operation.openAPI();
            if (openAPI != null && openAPI.getComponents() != null &&
                openAPI.getComponents().getSchemas() != null &&
                openAPI.getComponents().getSchemas().containsKey(schemaName)) {

                return openAPI.getComponents().getSchemas().get(schemaName);
            }
        }
        return null;
    }

    /**
     * Resolve a parameter schema by name
     */
    private Schema<?> resolveParameterSchema(String paramName) {
        for (OpenApiDefinitionService.ApiOperation operation : openApiDefinitionService.getOperationCache().values()) {
            io.swagger.v3.oas.models.OpenAPI openAPI = operation.openAPI();
            if (openAPI != null && openAPI.getComponents() != null &&
                openAPI.getComponents().getParameters() != null &&
                openAPI.getComponents().getParameters().containsKey(paramName)) {

                return openAPI.getComponents().getParameters().get(paramName).getSchema();
            }
        }
        return null;
    }

    public String convertParametersToJsonSchema(io.swagger.v3.oas.models.Operation operation) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        if (operation.getParameters() != null) {
            for (Parameter parameter : operation.getParameters()) {
                Map<String, Object> property = new HashMap<>();

                // Handle parameter references
                if (parameter.get$ref() != null) {
                    // This is a reference to a parameter, try to resolve it
                    String ref = parameter.get$ref();
                    log.debug("Processing parameter reference: {}", ref);

                    if (ref.startsWith("#/components/parameters/")) {
                        String paramName = ref.substring("#/components/parameters/".length());
                        Schema<?> resolvedParamSchema = resolveParameterSchema(paramName);

                        if (resolvedParamSchema != null) {
                            // We found the schema, use it
                            property.put("type", resolvedParamSchema.getType() != null ?
                                resolvedParamSchema.getType() : "string");
                            property.put("description", "Parameter reference: " + ref);

                            // Add any format information if available
                            if (resolvedParamSchema.getFormat() != null) {
                                property.put("format", resolvedParamSchema.getFormat());
                            }

                            // Handle enum values for referenced parameters
                            if (resolvedParamSchema.getEnum() != null && !resolvedParamSchema.getEnum().isEmpty()) {
                                property.put("enum", resolvedParamSchema.getEnum());
                                log.debug("Added enum values for referenced parameter {}: {}", paramName, resolvedParamSchema.getEnum());
                            }
                        } else {
                            // Couldn't resolve, use safe defaults
                            property.put("type", "string");
                            property.put("description", "Unresolved parameter reference: " + ref);
                        }
                    } else {
                        // Unknown reference format, use safe defaults
                        property.put("type", "string");
                        property.put("description", "Unknown parameter reference: " + ref);
                    }

                    properties.put(paramName(ref), property);
                    continue;
                }

                // Handle direct parameters (not references)
                Schema<?> paramSchema = parameter.getSchema();
                if (paramSchema != null) {
                    property.put("type", paramSchema.getType() != null ? paramSchema.getType() : "string");
                    property.put("description", parameter.getDescription() != null ?
                        parameter.getDescription() : "Parameter: " + parameter.getName());

                    // Add format information if available
                    if (paramSchema.getFormat() != null) {
                        property.put("format", paramSchema.getFormat());
                    }

                    // Handle enum values
                    if (paramSchema.getEnum() != null && !paramSchema.getEnum().isEmpty()) {
                        property.put("enum", paramSchema.getEnum());
                        log.debug("Added enum values for parameter {}: {}", parameter.getName(), paramSchema.getEnum());
                    }

                    //Handle items for arrays
                    if ("array".equals(paramSchema.getType()) && paramSchema.getItems() != null) {
                        Schema<?> itemsSchema = paramSchema.getItems();
                        Map<String, Object> itemsMap = convertSchemaToMap(itemsSchema);
                        property.put("items", itemsMap);
                        log.debug("Added items schema for array parameter {}: {}", parameter.getName(), itemsMap);
                    }

                } else {
                    // No schema available, use safe defaults
                    property.put("type", "string");
                    property.put("description", parameter.getDescription() != null ?
                        parameter.getDescription() : "Parameter: " + parameter.getName());
                }
                properties.put(parameter.getName(), property);
            }
        }
        if (operation.getRequestBody() != null) {
            // Extract detailed schema from request body instead of using a generic object
            try {
                io.swagger.v3.oas.models.media.Content content = operation.getRequestBody().getContent();
                if (content != null) {
                    // Try common media types
                    io.swagger.v3.oas.models.media.MediaType mediaType =
                        content.get("application/json") != null ? content.get("application/json") :
                        content.get("application/xml") != null ? content.get("application/xml") :
                        content.get("*/*");

                    if (mediaType != null && mediaType.getSchema() != null) {
                        Schema<?> requestSchema = mediaType.getSchema();
                        Map<String, Object> requestBodySchema = convertSchemaToMap(requestSchema);
                        properties.put("requestBody", requestBodySchema);
                        log.info("Added detailed schema for request body: {}", requestBodySchema);
                    } else {
                        // Fallback to generic object if no schema is available
                        properties.put("requestBody", Map.of("type", "object", "description", "Request body"));
                        log.warn("No schema found in request body, using generic object");
                    }
                } else {
                    properties.put("requestBody", Map.of("type", "object", "description", "Request body"));
                    log.warn("No content found in request body, using generic object");
                }
            } catch (Exception e) {
                log.error("Error processing request body schema", e);
                properties.put("requestBody", Map.of("type", "object", "description", "Request body"));
            }
        }
        schema.put("properties", properties);
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting parameters to JSON schema", e);
        }
    }
}
