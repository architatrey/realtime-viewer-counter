package com.example.realtimeviewercounter.dto;

public record ViewerCountResponse(
    String resourceId,
    long viewerCount
) {}
