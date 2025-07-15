package com.siri.api.mcp.mcp_openapi_server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "openapi")
@Data
public class OpenApiProperties {

    private List<Document> documents;

    public List<Document> getDocuments() {
        return documents;
    }

    public void setDocuments(List<Document> documents) {
        this.documents = documents;
    }

    @Data
    public static class Document {
        private String name;
        private String location;
        private String overrideUrl;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public String getOverrideUrl() {
            return overrideUrl;
        }

        public void setOverrideUrl(String overrideUrl) {
            this.overrideUrl = overrideUrl;
        }
    }
}
