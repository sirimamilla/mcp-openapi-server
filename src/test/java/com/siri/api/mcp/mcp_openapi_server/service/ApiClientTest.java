package com.siri.api.mcp.mcp_openapi_server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siri.api.mcp.mcp_openapi_server.config.OpenApiProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.servers.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ApiClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private OpenApiDefinitionService openApiDefinitionService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ApiClient apiClient;

    private OpenAPI mockOpenAPI;
    private OpenApiProperties.Document mockDocument;
    private Operation mockOperation;

    @BeforeEach
    void setUp() {
        // Set up mock document
        mockDocument = new OpenApiProperties.Document();
        mockDocument.setName("Test API");
        mockDocument.setLocation("test-location");

        // Set up mock OpenAPI
        mockOpenAPI = new OpenAPI();
        Server server = new Server();
        server.setUrl("http://default-server.com");
        mockOpenAPI.setServers(Collections.singletonList(server));

        // Set up mock operation
        mockOperation = new Operation();
        mockOperation.setSummary("Test Operation");
    }

    @Test
    void shouldInvokeApiWithDefaultServer() throws Exception {
        // Arrange
        String operationId = "testOperation";
        String path = "/test/path";
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("param1", "value1");

        // Mock the operation lookup
        OpenApiDefinitionService.ApiOperation apiOperation =
            new OpenApiDefinitionService.ApiOperation(
                mockDocument, mockOpenAPI, path, PathItem.HttpMethod.GET, mockOperation
            );
        when(openApiDefinitionService.getOperation(operationId))
            .thenReturn(Optional.of(apiOperation));

        // Mock the REST call - create ResponseEntity with proper headers
        Map<String, String> responseBody = Collections.singletonMap("result", "success");
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Content-Type", "application/json");
        ResponseEntity<Object> mockResponse = new ResponseEntity<>(responseBody, headers, HttpStatus.OK);

        when(restTemplate.exchange(
            anyString(),
            any(HttpMethod.class),
            any(HttpEntity.class),
            eq(Object.class)))
            .thenReturn(mockResponse);

        // Act
        Object result = apiClient.invoke(operationId, arguments);

        // Assert - expecting the actual parsed object
        assertEquals(responseBody, result);
    }

    @Test
    void shouldUseOverrideUrlWhenProvided() throws Exception {
        // Arrange
        String operationId = "testOperation";
        String path = "/test/path";
        Map<String, Object> arguments = new HashMap<>();

        // Set override URL
        mockDocument.setOverrideUrl("http://override-url.com");

        // Mock the operation lookup
        OpenApiDefinitionService.ApiOperation apiOperation =
            new OpenApiDefinitionService.ApiOperation(
                mockDocument, mockOpenAPI, path, PathItem.HttpMethod.GET, mockOperation
            );
        when(openApiDefinitionService.getOperation(operationId))
            .thenReturn(Optional.of(apiOperation));

        // Mock the REST call - create ResponseEntity with proper headers
        Map<String, String> responseBody = Collections.singletonMap("result", "success");
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Content-Type", "application/json");
        ResponseEntity<Object> mockResponse = new ResponseEntity<>(responseBody, headers, HttpStatus.OK);

        when(restTemplate.exchange(
            anyString(),
            any(HttpMethod.class),
            any(HttpEntity.class),
            eq(Object.class)))
            .thenReturn(mockResponse);

        // Act
        Object result = apiClient.invoke(operationId, arguments);

        // Assert - expecting the actual parsed object
        assertEquals(responseBody, result);
    }

    @Test
    void shouldThrowExceptionWhenOperationNotFound() {
        // Arrange
        String operationId = "nonExistingOperation";
        Map<String, Object> arguments = new HashMap<>();

        when(openApiDefinitionService.getOperation(operationId))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            apiClient.invoke(operationId, arguments);
        });
    }
}
