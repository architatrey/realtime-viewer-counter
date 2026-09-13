package com.example.realtimeviewercounter.dto;

import java.time.Instant;

public record HeartbeatResponse(
    String resourceId,
    String sessionId,
    long viewerCount,
    Instant expiresAt
) {}
