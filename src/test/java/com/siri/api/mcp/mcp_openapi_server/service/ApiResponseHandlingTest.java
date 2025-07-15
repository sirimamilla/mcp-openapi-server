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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiResponseHandlingTest {

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
        mockDocument = new OpenApiProperties.Document();
        mockDocument.setName("Test API");
        mockDocument.setLocation("test-location");

        mockOpenAPI = new OpenAPI();
        Server server = new Server();
        server.setUrl("http://test-server.com");
        mockOpenAPI.setServers(Collections.singletonList(server));

        mockOperation = new Operation();
        mockOperation.setSummary("Test Operation");
    }

    @Test
    void shouldReturnParsedJsonObjectForJsonResponse() throws Exception {
        // Arrange
        String operationId = "getJsonData";
        String path = "/json-endpoint";
        Map<String, Object> arguments = new HashMap<>();

        OpenApiDefinitionService.ApiOperation apiOperation =
            new OpenApiDefinitionService.ApiOperation(
                mockDocument, mockOpenAPI, path, PathItem.HttpMethod.GET, mockOperation
            );
        when(openApiDefinitionService.getOperation(operationId))
            .thenReturn(Optional.of(apiOperation));

        // Mock JSON response
        Map<String, Object> jsonResponseData = new HashMap<>();
        jsonResponseData.put("id", 123);
        jsonResponseData.put("name", "Test Pet");
        jsonResponseData.put("status", "available");
        jsonResponseData.put("tags", List.of("friendly", "playful"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Object> mockResponse = new ResponseEntity<>(jsonResponseData, headers, HttpStatus.OK);

        when(restTemplate.exchange(
            anyString(),
            any(HttpMethod.class),
            any(HttpEntity.class),
            eq(Object.class)))
            .thenReturn(mockResponse);

        // Act
        Object result = apiClient.invoke(operationId, arguments);

        // Assert
        assertInstanceOf(Map.class, result, "Should return parsed JSON as Map");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;

        assertEquals(123, resultMap.get("id"));
        assertEquals("Test Pet", resultMap.get("name"));
        assertEquals("available", resultMap.get("status"));
        assertInstanceOf(List.class, resultMap.get("tags"));
    }

    @Test
    void shouldReturnParsedArrayForJsonArrayResponse() throws Exception {
        // Arrange
        String operationId = "getJsonArray";
        String path = "/json-array-endpoint";
        Map<String, Object> arguments = new HashMap<>();

        OpenApiDefinitionService.ApiOperation apiOperation =
            new OpenApiDefinitionService.ApiOperation(
                mockDocument, mockOpenAPI, path, PathItem.HttpMethod.GET, mockOperation
            );
        when(openApiDefinitionService.getOperation(operationId))
            .thenReturn(Optional.of(apiOperation));

        // Mock JSON array response
        List<Map<String, Object>> jsonArrayData = List.of(
            Map.of("id", 1, "name", "Pet 1"),
            Map.of("id", 2, "name", "Pet 2")
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Object> mockResponse = new ResponseEntity<>(jsonArrayData, headers, HttpStatus.OK);

        when(restTemplate.exchange(
            anyString(),
            any(HttpMethod.class),
            any(HttpEntity.class),
            eq(Object.class)))
            .thenReturn(mockResponse);

        // Act
        Object result = apiClient.invoke(operationId, arguments);

        // Assert
        assertInstanceOf(List.class, result, "Should return parsed JSON array as List");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resultList = (List<Map<String, Object>>) result;

        assertEquals(2, resultList.size());
        assertEquals(1, resultList.get(0).get("id"));
        assertEquals("Pet 1", resultList.get(0).get("name"));
        assertEquals(2, resultList.get(1).get("id"));
        assertEquals("Pet 2", resultList.get(1).get("name"));
    }

    @Test
    void shouldReturnStringForPlainTextResponse() throws Exception {
        // Arrange
        String operationId = "getTextData";
        String path = "/text-endpoint";
        Map<String, Object> arguments = new HashMap<>();

        OpenApiDefinitionService.ApiOperation apiOperation =
            new OpenApiDefinitionService.ApiOperation(
                mockDocument, mockOpenAPI, path, PathItem.HttpMethod.GET, mockOperation
            );
        when(openApiDefinitionService.getOperation(operationId))
            .thenReturn(Optional.of(apiOperation));

        // Mock plain text response
        String textData = "Operation completed successfully";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        ResponseEntity<Object> mockResponse = new ResponseEntity<>(textData, headers, HttpStatus.OK);

        when(restTemplate.exchange(
            anyString(),
            any(HttpMethod.class),
            any(HttpEntity.class),
            eq(Object.class)))
            .thenReturn(mockResponse);

        // Act
        Object result = apiClient.invoke(operationId, arguments);

        // Assert
        assertInstanceOf(String.class, result, "Should return plain text as String");
        assertEquals("Operation completed successfully", result);
    }

    @Test
    void shouldReturnParsedObjectForApplicationJsonVariant() throws Exception {
        // Arrange
        String operationId = "getJsonVariant";
        String path = "/json-variant-endpoint";
        Map<String, Object> arguments = new HashMap<>();

        OpenApiDefinitionService.ApiOperation apiOperation =
            new OpenApiDefinitionService.ApiOperation(
                mockDocument, mockOpenAPI, path, PathItem.HttpMethod.GET, mockOperation
            );
        when(openApiDefinitionService.getOperation(operationId))
            .thenReturn(Optional.of(apiOperation));

        // Mock JSON response with variant content type
        Map<String, Object> jsonData = Map.of("message", "Success", "code", 200);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.api+json"));
        ResponseEntity<Object> mockResponse = new ResponseEntity<>(jsonData, headers, HttpStatus.OK);

        when(restTemplate.exchange(
            anyString(),
            any(HttpMethod.class),
            any(HttpEntity.class),
            eq(Object.class)))
            .thenReturn(mockResponse);

        // Act
        Object result = apiClient.invoke(operationId, arguments);

        // Assert
        assertInstanceOf(Map.class, result, "Should return parsed JSON variant as Map");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;

        assertEquals("Success", resultMap.get("message"));
        assertEquals(200, resultMap.get("code"));
    }

    @Test
    void shouldHandleNullContentType() throws Exception {
        // Arrange
        String operationId = "getNullContentType";
        String path = "/null-content-type-endpoint";
        Map<String, Object> arguments = new HashMap<>();

        OpenApiDefinitionService.ApiOperation apiOperation =
            new OpenApiDefinitionService.ApiOperation(
                mockDocument, mockOpenAPI, path, PathItem.HttpMethod.GET, mockOperation
            );
        when(openApiDefinitionService.getOperation(operationId))
            .thenReturn(Optional.of(apiOperation));

        // Mock response with null content type
        String responseData = "Some response data";

        HttpHeaders headers = new HttpHeaders();
        // Don't set content type (will be null)
        ResponseEntity<Object> mockResponse = new ResponseEntity<>(responseData, headers, HttpStatus.OK);

        when(restTemplate.exchange(
            anyString(),
            any(HttpMethod.class),
            any(HttpEntity.class),
            eq(Object.class)))
            .thenReturn(mockResponse);

        // Act
        Object result = apiClient.invoke(operationId, arguments);

        // Assert
        assertEquals("Some response data", result, "Should return data as string when content type is null");
    }
}
