package com.example.realtimeviewercounter.controller;

import com.example.realtimeviewercounter.dto.HeartbeatRequest;
import com.example.realtimeviewercounter.dto.HeartbeatResponse;
import com.example.realtimeviewercounter.dto.ViewerCountResponse;
import com.example.realtimeviewercounter.service.PresenceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/v1")
@Validated
public class PresenceController {

    private final PresenceService presenceService;

    public PresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @PostMapping("/presence/heartbeat")
    public Mono<ResponseEntity<HeartbeatResponse>> heartbeat(
            @RequestBody @Valid HeartbeatRequest request) {
        return presenceService.heartbeat(request)
                .map(ResponseEntity::ok);
    }
    @GetMapping("/resources/{resourceId}/viewers/count")
    public Mono<ResponseEntity<ViewerCountResponse>> getViewerCount(
            @PathVariable String resourceId) {
        return presenceService.getViewerCount(resourceId)
                .map(ResponseEntity::ok);
    }
}
