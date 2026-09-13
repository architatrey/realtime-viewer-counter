package com.example.realtimeviewercounter.service;

import com.example.realtimeviewercounter.dto.HeartbeatRequest;
import com.example.realtimeviewercounter.dto.HeartbeatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Service
public class PresenceService {

    private static final String KEY_PREFIX = "resource_tracker:";
    private static final Duration KEY_TTL = Duration.ofHours(24);

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final Duration sessionTtl;

    public PresenceService(
            ReactiveRedisTemplate<String, String> redisTemplate,
            @Value("${presence.session.ttl-seconds:30}") long sessionTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.sessionTtl = Duration.ofSeconds(sessionTtlSeconds);
    }

    public Mono<HeartbeatResponse> heartbeat(HeartbeatRequest request) {
        String key = KEY_PREFIX + request.resourceId();
        Instant expiresAt = Instant.now().plus(sessionTtl);
        double score = expiresAt.toEpochMilli();

        return redisTemplate.opsForZSet()
                .add(key, request.sessionId(), score)
                .then(redisTemplate.expire(key, KEY_TTL))
                .then(countActive(key))
                .map(count -> new HeartbeatResponse(
                        request.resourceId(),
                        request.sessionId(),
                        count,
                        expiresAt
                ));
    }

    private Mono<Long> countActive(String key) {
        double now = Instant.now().toEpochMilli();
        return redisTemplate.opsForZSet()
                .count(key, Range.from(Range.Bound.inclusive(now)).to(Range.Bound.unbounded()));
    }
}
