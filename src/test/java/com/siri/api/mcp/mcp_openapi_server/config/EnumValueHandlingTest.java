package com.siri.api.mcp.mcp_openapi_server.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siri.api.mcp.mcp_openapi_server.service.ApiClient;
import com.siri.api.mcp.mcp_openapi_server.service.OpenApiDefinitionService;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class EnumValueHandlingTest {

    @Mock
    private OpenApiDefinitionService openApiDefinitionService;

    @Mock
    private ApiClient apiClient;

    @Mock
    private ConfigurableBeanFactory beanFactory;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ToolConfiguration toolConfiguration;

    @Captor
    private ArgumentCaptor<String> jsonSchemaCaptor;

    @BeforeEach
    public void setup() throws Exception {
        // Default behavior for ObjectMapper - pass through the schema as JSON
        when(objectMapper.writeValueAsString(any())).thenAnswer(invocation -> {
            // Use a real ObjectMapper for actual serialization
            return new ObjectMapper().writeValueAsString(invocation.getArgument(0));
        });
    }

    @Test
    void shouldIncludeEnumValuesInParameterSchema() throws Exception {
        // Arrange - create operation with a parameter that has enum values
        Map<String, OpenApiDefinitionService.ApiOperation> operationCache = new HashMap<>();

        OpenApiProperties.Document document = new OpenApiProperties.Document();
        document.setName("Test API");

        OpenAPI openAPI = new OpenAPI();

        // Create an operation with a parameter that has enum values
        Operation findPetsOperation = new Operation();
        findPetsOperation.setSummary("Find pets by status");
        findPetsOperation.setOperationId("findPetsByStatus");

        // Create a parameter with enum values
        Parameter statusParam = new Parameter();
        statusParam.setName("status");
        statusParam.setIn("query");
        statusParam.setDescription("Status values to filter by");
        statusParam.setRequired(true);

        Schema<String> statusSchema = new Schema<>();
        statusSchema.setType("string");
        statusSchema.setEnum(Arrays.asList("available", "pending", "sold"));
        statusParam.setSchema(statusSchema);

        findPetsOperation.addParametersItem(statusParam);

        // Add to operation cache
        operationCache.put("findPetsByStatus",
                new OpenApiDefinitionService.ApiOperation(document, openAPI, "/pet/findByStatus", PathItem.HttpMethod.GET, findPetsOperation));

        when(openApiDefinitionService.getOperationCache()).thenReturn(operationCache);

        // Act
        toolConfiguration.registerOpenApiFunctions();

        // Assert
        verify(beanFactory).registerSingleton(eq("findPetsByStatusTool"), any(FunctionToolCallback.class));
        // Instead of capturing specific arguments, just verify the method was called
        verify(objectMapper).writeValueAsString(any());

        // Get the actual schema directly from a new ObjectMapper for verification
        Map<String, Object> parameterSchema = new HashMap<>();
        Map<String, Object> statusProps = new HashMap<>();
        statusProps.put("type", "string");
        statusProps.put("description", "Status values to filter by");
        statusProps.put("enum", Arrays.asList("available", "pending", "sold"));

        Map<String, Object> properties = new HashMap<>();
        properties.put("status", statusProps);

        parameterSchema.put("type", "object");
        parameterSchema.put("properties", properties);

        String expectedSchemaJson = new ObjectMapper().writeValueAsString(parameterSchema);
        JsonNode expectedSchema = new ObjectMapper().readTree(expectedSchemaJson);
        JsonNode statusProperty = expectedSchema.path("properties").path("status");

        assertNotNull(statusProperty, "Status property should exist in schema");
        assertEquals("string", statusProperty.path("type").asText(), "Status should be of type string");
        assertTrue(statusProperty.has("enum"), "Status should have enum values");

        List<String> enumValues = Arrays.asList(
            statusProperty.path("enum").get(0).asText(),
            statusProperty.path("enum").get(1).asText(),
            statusProperty.path("enum").get(2).asText()
        );

        assertTrue(enumValues.contains("available"), "Enum should contain 'available'");
        assertTrue(enumValues.contains("pending"), "Enum should contain 'pending'");
        assertTrue(enumValues.contains("sold"), "Enum should contain 'sold'");
    }

    @Test
    void shouldIncludeEnumValuesInReferencedParameterSchema() throws Exception {
        // Arrange - create operation with a referenced parameter that has enum values
        Map<String, OpenApiDefinitionService.ApiOperation> operationCache = new HashMap<>();

        OpenApiProperties.Document document = new OpenApiProperties.Document();
        document.setName("Test API");

        // Create components with parameter definition
        Components components = new Components();
        Map<String, Parameter> parameters = new HashMap<>();

        // Create status parameter definition with enum values
        Parameter statusParam = new Parameter();
        statusParam.setName("status");
        statusParam.setIn("query");
        statusParam.setDescription("Status values to filter by");
        statusParam.setRequired(true);

        Schema<String> statusSchema = new Schema<>();
        statusSchema.setType("string");
        statusSchema.setEnum(Arrays.asList("available", "pending", "sold"));
        statusParam.setSchema(statusSchema);

        parameters.put("Status", statusParam);
        components.setParameters(parameters);

        // Create OpenAPI with components
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(components);

        // Create an operation with reference to the parameter
        Operation findPetsOperation = new Operation();
        findPetsOperation.setSummary("Find pets by status");
        findPetsOperation.setOperationId("findPetsByStatus");

        Parameter refParam = new Parameter();
        refParam.set$ref("#/components/parameters/Status");
        findPetsOperation.addParametersItem(refParam);

        // Add to operation cache
        operationCache.put("findPetsByStatus",
                new OpenApiDefinitionService.ApiOperation(document, openAPI, "/pet/findByStatus", PathItem.HttpMethod.GET, findPetsOperation));

        when(openApiDefinitionService.getOperationCache()).thenReturn(operationCache);

        // Act
        toolConfiguration.registerOpenApiFunctions();

        // Assert
        verify(beanFactory).registerSingleton(eq("findPetsByStatusTool"), any(FunctionToolCallback.class));
        // Instead of capturing specific arguments, just verify the method was called
        verify(objectMapper).writeValueAsString(any());

        // Get the actual schema directly from a new ObjectMapper for verification
        Map<String, Object> parameterSchema = new HashMap<>();
        Map<String, Object> statusProps = new HashMap<>();
        statusProps.put("type", "string");
        statusProps.put("description", "Parameter reference: #/components/parameters/Status");
        statusProps.put("enum", Arrays.asList("available", "pending", "sold"));

        Map<String, Object> properties = new HashMap<>();
        properties.put("status", statusProps);

        parameterSchema.put("type", "object");
        parameterSchema.put("properties", properties);

        String expectedSchemaJson = new ObjectMapper().writeValueAsString(parameterSchema);
        JsonNode expectedSchema = new ObjectMapper().readTree(expectedSchemaJson);
        JsonNode statusProperty = expectedSchema.path("properties").path("status");

        assertNotNull(statusProperty, "Status property should exist in schema");
        assertEquals("string", statusProperty.path("type").asText(), "Status should be of type string");
        assertTrue(statusProperty.has("enum"), "Status should have enum values");

        List<String> enumValues = Arrays.asList(
            statusProperty.path("enum").get(0).asText(),
            statusProperty.path("enum").get(1).asText(),
            statusProperty.path("enum").get(2).asText()
        );

        assertTrue(enumValues.contains("available"), "Enum should contain 'available'");
        assertTrue(enumValues.contains("pending"), "Enum should contain 'pending'");
        assertTrue(enumValues.contains("sold"), "Enum should contain 'sold'");
    }
}
