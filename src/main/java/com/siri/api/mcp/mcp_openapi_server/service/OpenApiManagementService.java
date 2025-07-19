package com.siri.api.mcp.mcp_openapi_server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siri.api.mcp.mcp_openapi_server.config.OpenApiProperties;
import com.siri.api.mcp.mcp_openapi_server.config.ToolConfiguration;
import com.siri.api.mcp.mcp_openapi_server.dto.ToolInfo;
import com.siri.api.mcp.mcp_openapi_server.service.ApiClient;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.function.BiFunction;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.support.DefaultSingletonBeanRegistry;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class OpenApiManagementService {
    
    private final OpenApiDefinitionService openApiDefinitionService;
    private final ToolConfiguration toolConfiguration;
    private final ConfigurableBeanFactory beanFactory;
    private final ObjectMapper objectMapper;
    private final ApiClient apiClient;
    
    @Autowired
    private McpSyncServer mcpSyncServer;
    
    private final Map<String, OpenApiProperties.Document> dynamicDocuments = new ConcurrentHashMap<>();
    
    public List<ToolInfo> listTools() {
        return openApiDefinitionService.getOperationCache().entrySet().stream()
                .map(entry -> new ToolInfo(
                    entry.getKey(),
                    entry.getValue().operation().getSummary(),
                    entry.getValue().document().getName()
                ))
                .collect(Collectors.toList());
    }
    
    public void addOpenApiFromUri(String name, String uri, String overrideUrl) throws Exception {
        if (dynamicDocuments.containsKey(name)) {
            throw new IllegalArgumentException("OpenAPI document with name '" + name + "' already exists");
        }
        
        OpenApiProperties.Document document = new OpenApiProperties.Document();
        document.setName(name);
        document.setLocation(uri);
        document.setOverrideUrl(overrideUrl);
        
        loadAndRegisterOpenApi(document);
        dynamicDocuments.put(name, document);
        log.info("Successfully added OpenAPI from URI: {} with name: {}", uri, name);
    }
    
    public void addOpenApiFromFileContent(String name, String content, String overrideUrl, String filename) throws Exception {
        if (dynamicDocuments.containsKey(name)) {
            throw new IllegalArgumentException("OpenAPI document with name '" + name + "' already exists");
        }
        
        String tempPath = saveFileContent(content, filename != null ? filename : "uploaded-spec.json");
        
        OpenApiProperties.Document document = new OpenApiProperties.Document();
        document.setName(name);
        document.setLocation(tempPath);
        document.setOverrideUrl(overrideUrl);
        
        loadAndRegisterOpenApi(document);
        dynamicDocuments.put(name, document);
        log.info("Successfully added OpenAPI from file content with name: {}", name);
    }
    
    public void removeOpenApi(String name) {
        OpenApiProperties.Document document = dynamicDocuments.remove(name);
        if (document == null) {
            throw new IllegalArgumentException("OpenAPI document not found: " + name);
        }
        
        removeToolsForDocument(document);
        log.info("Successfully removed OpenAPI document: {}", name);
    }
    
    private void loadAndRegisterOpenApi(OpenApiProperties.Document document) throws Exception {
        OpenAPIV3Parser parser = new OpenAPIV3Parser();
        OpenAPI openAPI = parser.read(document.getLocation());
        
        if (openAPI == null) {
            throw new IllegalArgumentException("Failed to parse OpenAPI from: " + document.getLocation());
        }
        
        openAPI.getPaths().forEach((path, pathItem) ->
            pathItem.readOperationsMap().forEach((httpMethod, operation) -> {
                if (operation.getOperationId() != null) {
                    OpenApiDefinitionService.ApiOperation apiOperation = 
                        new OpenApiDefinitionService.ApiOperation(document, openAPI, path, httpMethod, operation);
                    openApiDefinitionService.getOperationCache().put(operation.getOperationId(), apiOperation);
                }
            })
        );
        
        registerMcpToolsForDocument(document);
    }
    
    private String saveFileContent(String content, String originalFilename) throws IOException {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("File content is empty");
        }
        
        Path uploadDir = Paths.get("uploads");
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
        
        String filename = System.currentTimeMillis() + "_" + (originalFilename != null ? originalFilename : "uploaded-spec.json");
        Path filePath = uploadDir.resolve(filename);
        
        Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
        
        return filePath.toString();
    }
    
    private void registerMcpToolsForDocument(OpenApiProperties.Document document) {
        openApiDefinitionService.getOperationCache().entrySet().stream()
            .filter(entry -> entry.getValue().document().getName().equals(document.getName()))
            .forEach(entry -> {
                String operationId = entry.getKey();
                OpenApiDefinitionService.ApiOperation apiOperation = entry.getValue();
                
                try {
                    toolConfiguration.registerSingleOperation(operationId, apiOperation);
                    
                    String description = apiOperation.operation().getSummary() != null ?
                            apiOperation.operation().getSummary() :
                            "Operation: " + operationId;
                    
                    String schema = toolConfiguration.convertParametersToJsonSchema(apiOperation.operation());
                    
                    McpSchema.Tool mcpTool = new McpSchema.Tool(operationId, description, schema);
                    
                    BiFunction<McpSyncServerExchange, Map<String, Object>, McpSchema.CallToolResult> callHandler = 
                        (exchange, params) -> {
                            try {
                                Object result = apiClient.invoke(operationId, params);
                                return new McpSchema.CallToolResult(
                                    List.of(new McpSchema.TextContent(result.toString())),
                                    false
                                );
                            } catch (Exception e) {
                                log.error("Error invoking tool {}: {}", operationId, e.getMessage(), e);
                                return new McpSchema.CallToolResult(
                                    List.of(new McpSchema.TextContent("Error: " + e.getMessage())),
                                    true
                                );
                            }
                        };
                    
                    SyncToolSpecification toolSpec = new SyncToolSpecification(mcpTool, callHandler);
                    mcpSyncServer.addTool(toolSpec);
                    log.info("Successfully registered MCP tool: {}", operationId);
                    
                } catch (Exception e) {
                    log.error("Failed to register MCP tool {}: {}", operationId, e.getMessage(), e);
                }
            });
    }
    
    private void removeToolsForDocument(OpenApiProperties.Document document) {
        openApiDefinitionService.getOperationCache().entrySet().removeIf(entry -> {
            if (entry.getValue().document().getName().equals(document.getName())) {
                String operationId = entry.getKey();
                
                try {
                    mcpSyncServer.removeTool(operationId);
                    log.info("Removed MCP tool: {}", operationId);
                    
                    String beanName = operationId + "Tool";
                    if (beanFactory.containsSingleton(beanName)) {
                        if (beanFactory instanceof DefaultSingletonBeanRegistry) {
                            ((DefaultSingletonBeanRegistry) beanFactory).destroySingleton(beanName);
                            log.info("Removed tool bean: {}", beanName);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error removing MCP tool {}: {}", operationId, e.getMessage());
                }
                
                return true;
            }
            return false;
        });
    }
}
