package com.siri.api.mcp.mcp_openapi_server.dto;

import lombok.Data;

@Data
public class AddUriRequest {
    private String name;
    private String uri;
    private String overrideUrl;
}
