package com.siri.api.mcp.mcp_openapi_server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siri.api.mcp.mcp_openapi_server.service.ApiClient;
import com.siri.api.mcp.mcp_openapi_server.service.OpenApiDefinitionService;
import io.modelcontextprotocol.server.McpServer;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ToolResponseStructureTest {

    @Mock
    private OpenApiDefinitionService openApiDefinitionService;

    @Mock
    private ApiClient apiClient;

    @Mock
    private ConfigurableBeanFactory beanFactory;

    @Mock
    private McpServer mcpServer;
    private ObjectMapper objectMapper;
    private ToolConfiguration toolConfiguration;


    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        toolConfiguration = new ToolConfiguration(openApiDefinitionService, apiClient, beanFactory, objectMapper, mcpServer);
    }

    @Test
    void testSimpleToolCallResultConverter_withMapResult() {
        // Given
        ToolConfiguration.SimpleToolCallResultConverter converter =
            new ToolConfiguration.SimpleToolCallResultConverter();

        Map<String, Object> mapResult = Map.of("id", 1, "name", "Fluffy");

        // When
        String result = converter.convert(mapResult, Map.class);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).contains("id=1");
        assertThat(result).contains("name=Fluffy");
    }

    @Test
    void testSimpleToolCallResultConverter_withStringResult() {
        // Given
        ToolConfiguration.SimpleToolCallResultConverter converter =
            new ToolConfiguration.SimpleToolCallResultConverter();

        String jsonResult = "{\"id\":1,\"name\":\"Fluffy\"}";

        // When
        String result = converter.convert(jsonResult, String.class);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(jsonResult);
    }

    @Test
    void testSimpleToolCallResultConverter_withNullResult() {
        // Given
        ToolConfiguration.SimpleToolCallResultConverter converter =
            new ToolConfiguration.SimpleToolCallResultConverter();

        // When
        String result = converter.convert(null, Object.class);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo("null");
    }

    @Test
    void testSimpleToolCallResultConverter_withListResult() {
        // Given
        ToolConfiguration.SimpleToolCallResultConverter converter =
            new ToolConfiguration.SimpleToolCallResultConverter();

        List<Map<String, Object>> listResult = List.of(
            Map.of("id", 1, "name", "Pet1"),
            Map.of("id", 2, "name", "Pet2")
        );

        // When
        String result = converter.convert(listResult, List.class);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).contains("Pet1");
        assertThat(result).contains("Pet2");
    }

    @Test
    void testSimpleToolCallResultConverter_withComplexObject() {
        // Given
        ToolConfiguration.SimpleToolCallResultConverter converter =
            new ToolConfiguration.SimpleToolCallResultConverter();

        Map<String, Object> complexResult = Map.of(
            "id", 1,
            "name", "Fluffy",
            "category", Map.of("id", 2, "name", "Cats"),
            "tags", List.of(
                Map.of("id", 1, "name", "tag1"),
                Map.of("id", 2, "name", "tag2")
            )
        );

        // When
        String result = converter.convert(complexResult, Map.class);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).contains("Fluffy");
        assertThat(result).contains("Cats");
        assertThat(result).contains("tag1");
        assertThat(result).contains("tag2");
    }
}
