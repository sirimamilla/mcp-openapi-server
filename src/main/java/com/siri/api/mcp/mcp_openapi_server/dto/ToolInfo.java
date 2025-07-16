package com.siri.api.mcp.mcp_openapi_server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ToolInfo {
    private String operationId;
    private String description;
    private String documentName;
}
