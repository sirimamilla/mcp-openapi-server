package com.siri.api.mcp.mcp_openapi_server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siri.api.mcp.mcp_openapi_server.config.OpenApiProperties;
import com.siri.api.mcp.mcp_openapi_server.config.ToolConfiguration;
import com.siri.api.mcp.mcp_openapi_server.dto.ToolInfo;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        
        toolConfiguration.registerNewOperations(document);
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
    
    private void removeToolsForDocument(OpenApiProperties.Document document) {
        openApiDefinitionService.getOperationCache().entrySet().removeIf(entry -> {
            if (entry.getValue().document().getName().equals(document.getName())) {
                String operationId = entry.getKey();
                String beanName = operationId + "Tool";
                
                try {
                    if (beanFactory.containsSingleton(beanName)) {
                        if (beanFactory instanceof DefaultSingletonBeanRegistry) {
                            ((DefaultSingletonBeanRegistry) beanFactory).destroySingleton(beanName);
                            log.info("Removed tool bean: {}", beanName);
                        } else {
                            log.warn("Cannot remove bean {}: BeanFactory does not support singleton destruction", beanName);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error removing tool bean {}: {}", beanName, e.getMessage());
                }
                
                return true;
            }
            return false;
        });
    }
}
