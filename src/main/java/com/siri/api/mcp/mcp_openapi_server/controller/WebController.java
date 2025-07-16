package com.siri.api.mcp.mcp_openapi_server.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class WebController {
    
    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<Resource> index() {
        return Mono.just(new ClassPathResource("static/index.html"));
    }
}
