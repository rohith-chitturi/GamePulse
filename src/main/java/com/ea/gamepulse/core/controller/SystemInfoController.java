package com.ea.gamepulse.core.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Baseline controller exposing service metadata, operational status, and health info.
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemInfoController {

    @Value("${spring.application.name:gamepulse}")
    private String applicationName;

    @Value("${gamepulse.service.version:0.1.0-SNAPSHOT}")
    private String version;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        return ResponseEntity.ok(Map.of(
                "service", applicationName,
                "version", version,
                "status", "UP",
                "activeProfile", activeProfile,
                "timestamp", Instant.now().toString()
        ));
    }
}
