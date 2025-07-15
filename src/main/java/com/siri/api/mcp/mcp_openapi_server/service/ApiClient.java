package com.siri.api.mcp.mcp_openapi_server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siri.api.mcp.mcp_openapi_server.config.OpenApiProperties;
import com.siri.api.mcp.mcp_openapi_server.service.OpenApiDefinitionService.ApiOperation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiClient {

    private final RestTemplate restTemplate;
    private final OpenApiDefinitionService openApiDefinitionService;
    private final ObjectMapper objectMapper;

    public Object invoke(String operationId, Map<String, Object> arguments) {
        ApiOperation apiOperation = openApiDefinitionService.getOperation(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Operation not found: " + operationId));

        OpenApiProperties.Document doc = apiOperation.document();
        OpenAPI openAPI = apiOperation.openAPI();
        String path = apiOperation.path();
        PathItem.HttpMethod httpMethod = apiOperation.httpMethod();
        Operation operation = apiOperation.operation();

        String baseUrl = doc.getOverrideUrl() != null ? doc.getOverrideUrl() : openAPI.getServers().get(0).getUrl();
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(baseUrl);

        HttpHeaders headers = new HttpHeaders();
        Object requestBody = null;

        if (operation.getParameters() != null) {
            for (Parameter parameter : operation.getParameters()) {
                if (arguments.containsKey(parameter.getName())) {
                    String value = arguments.get(parameter.getName()).toString();
                    switch (parameter.getIn()) {
                        case "query":
                            uriBuilder.queryParam(parameter.getName(), value);
                            break;
                        case "path":
                            path = path.replace("{" + parameter.getName() + "}", value);
                            break;
                        case "header":
                            headers.add(parameter.getName(), value);
                            break;
                    }
                }
            }
        }
        uriBuilder.path(path);

        if (arguments.containsKey("requestBody")) {
            requestBody = arguments.get("requestBody");
            headers.add("Content-Type", "application/json");
        }

        try {
            String body = requestBody != null ? objectMapper.writeValueAsString(requestBody) : null;
            HttpEntity<String> httpEntity = new HttpEntity<>(body, headers);
            String url = uriBuilder.build(false).toUriString();

            // Use Object.class instead of String.class to let RestTemplate handle the response type automatically
            ResponseEntity<Object> response = restTemplate.exchange(url, HttpMethod.valueOf(httpMethod.name()), httpEntity, Object.class);

            Object responseBody = response.getBody();
            MediaType contentType = response.getHeaders().getContentType();

            log.debug("API response for operation {}: Content-Type={}, Body={}", operationId, contentType, responseBody);

            // If the response is already parsed as an object (JSON), return it directly
            if (responseBody != null && contentType != null) {
                if (contentType.includes(MediaType.APPLICATION_JSON) ||
                    contentType.includes(MediaType.APPLICATION_XML) ||
                    contentType.getSubtype().contains("json")) {
                    // Response is structured data, return as-is
                    return responseBody;
                }
            }

            // For other content types or if parsing failed, return as string
            return responseBody != null ? responseBody.toString() : null;

        } catch (Exception e) {
            log.error("Error invoking operation {}: {}", operationId, e.getMessage(), e);
            throw new RuntimeException("Error invoking " + operationId + " : " + e.getMessage(), e);
        }
    }
}
