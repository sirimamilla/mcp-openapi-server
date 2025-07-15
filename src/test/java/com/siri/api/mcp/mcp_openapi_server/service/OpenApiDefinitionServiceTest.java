package com.siri.api.mcp.mcp_openapi_server.service;

import com.siri.api.mcp.mcp_openapi_server.config.OpenApiProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OpenApiDefinitionServiceTest {

    @Mock
    private OpenApiProperties openApiProperties;

    @InjectMocks
    private OpenApiDefinitionService openApiDefinitionService;

    @BeforeEach
    void setUp() {
        OpenApiProperties.Document document = new OpenApiProperties.Document();
        document.setName("Pet Store");
        document.setLocation("src/test/resources/openapi/petstore.json");
        document.setOverrideUrl("http://test-override-url.com");

        when(openApiProperties.getDocuments()).thenReturn(Collections.singletonList(document));

        // Initialize the service manually since @PostConstruct won't be called in test
        openApiDefinitionService.init();
    }

    @Test
    void shouldLoadOperationsFromOpenApiSpec() {
        // Verify the operations were loaded from the petstore.json file
        Map<String, OpenApiDefinitionService.ApiOperation> operations = openApiDefinitionService.getOperationCache();

        assertFalse(operations.isEmpty(), "Operations should be loaded from the spec");
        assertTrue(operations.containsKey("getPetById"), "Should contain 'getPetById' operation");

        // Verify operation details
        OpenApiDefinitionService.ApiOperation operation = operations.get("getPetById");
        assertEquals("/pet/{petId}", operation.path());
        assertEquals(PathItem.HttpMethod.GET, operation.httpMethod());
    }

    @Test
    void shouldReturnOperationById() {
        // Test getting an existing operation
        Optional<OpenApiDefinitionService.ApiOperation> operation = openApiDefinitionService.getOperation("getPetById");

        assertTrue(operation.isPresent());
        assertEquals("Find pet by ID.", operation.get().operation().getSummary());

        // Test getting a non-existing operation
        Optional<OpenApiDefinitionService.ApiOperation> nonExisting = openApiDefinitionService.getOperation("nonExistingOperation");
        assertFalse(nonExisting.isPresent());
    }

    @Test
    void shouldRespectOverrideUrl() {
        // Verify that the override URL is properly stored
        Optional<OpenApiDefinitionService.ApiOperation> operation = openApiDefinitionService.getOperation("getPetById");

        assertTrue(operation.isPresent());
        assertEquals("http://test-override-url.com", operation.get().document().getOverrideUrl());
    }
}
