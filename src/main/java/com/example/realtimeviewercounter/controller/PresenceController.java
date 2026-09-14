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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/v1")
@Validated
public class PresenceController {

    private static final Logger log = LoggerFactory.getLogger(PresenceController.class);

    private final PresenceService presenceService;

    public PresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @PostMapping("/presence/heartbeat")
    public Mono<ResponseEntity<HeartbeatResponse>> heartbeat(
            @RequestBody @Valid HeartbeatRequest request) {
        log.info("Received heartbeat for resourceId: {}, sessionId: {}", request.resourceId(), request.sessionId());
        return presenceService.heartbeat(request)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.debug("Heartbeat processed successfully for resourceId: {}", request.resourceId()))
                .doOnError(error -> log.error("Error processing heartbeat for resourceId: {}", request.resourceId(), error));
    }

    @GetMapping("/resources/{resourceId}/viewers/count")
    public Mono<ResponseEntity<ViewerCountResponse>> getViewerCount(
            @PathVariable String resourceId) {
        log.info("Received request for viewer count for resourceId: {}", resourceId);
        return presenceService.getViewerCount(resourceId)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.debug("Viewer count retrieved successfully for resourceId: {}", resourceId))
                .doOnError(error -> log.error("Error retrieving viewer count for resourceId: {}", resourceId, error));
    }
}
