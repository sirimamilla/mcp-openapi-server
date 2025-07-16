package com.siri.api.mcp.mcp_openapi_server.dto;

import lombok.Data;

@Data
public class AddFileContentRequest {
    private String name;
    private String content;
    private String overrideUrl;
    private String filename;
}
