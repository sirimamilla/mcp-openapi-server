package com.siri.api.mcp.mcp_openapi_server.controller;

import com.siri.api.mcp.mcp_openapi_server.dto.AddUriRequest;
import com.siri.api.mcp.mcp_openapi_server.dto.AddFileContentRequest;
import com.siri.api.mcp.mcp_openapi_server.dto.ToolInfo;
import com.siri.api.mcp.mcp_openapi_server.service.OpenApiManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/openapi")
@Slf4j
@RequiredArgsConstructor
public class OpenApiController {
    
    private final OpenApiManagementService openApiManagementService;
    
    @GetMapping("/tools")
    public ResponseEntity<List<ToolInfo>> listTools() {
        try {
            List<ToolInfo> tools = openApiManagementService.listTools();
            return ResponseEntity.ok(tools);
        } catch (Exception e) {
            log.error("Error listing tools", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/add-uri")
    public ResponseEntity<String> addOpenApiFromUri(@RequestBody AddUriRequest request) {
        try {
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Name is required");
            }
            if (request.getUri() == null || request.getUri().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("URI is required");
            }
            
            openApiManagementService.addOpenApiFromUri(
                request.getName().trim(), 
                request.getUri().trim(), 
                request.getOverrideUrl() != null ? request.getOverrideUrl().trim() : null
            );
            return ResponseEntity.ok("OpenAPI spec added successfully from URI");
        } catch (Exception e) {
            log.error("Error adding OpenAPI from URI", e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
    
    @PostMapping("/add-file-content")
    public ResponseEntity<String> addOpenApiFromFileContent(@RequestBody AddFileContentRequest request) {
        try {
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Name is required");
            }
            if (request.getContent() == null || request.getContent().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("File content is required");
            }
            
            openApiManagementService.addOpenApiFromFileContent(
                request.getName().trim(), 
                request.getContent().trim(), 
                request.getOverrideUrl() != null && !request.getOverrideUrl().trim().isEmpty() ? request.getOverrideUrl().trim() : null,
                request.getFilename()
            );
            return ResponseEntity.ok("OpenAPI spec uploaded successfully");
        } catch (Exception e) {
            log.error("Error adding OpenAPI from file content", e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/remove/{name}")
    public ResponseEntity<String> removeOpenApi(@PathVariable String name) {
        try {
            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Name is required");
            }
            
            openApiManagementService.removeOpenApi(name.trim());
            return ResponseEntity.ok("OpenAPI spec removed successfully");
        } catch (Exception e) {
            log.error("Error removing OpenAPI", e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}
