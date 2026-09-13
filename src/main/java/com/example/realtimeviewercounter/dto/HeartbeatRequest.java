package com.example.realtimeviewercounter.dto;

import jakarta.validation.constraints.NotBlank;

public record HeartbeatRequest(
    @NotBlank(message = "resourceId cannot be blank") String resourceId,
    @NotBlank(message = "sessionId cannot be blank") String sessionId
) {}
