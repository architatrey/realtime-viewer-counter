# Realtime Viewer Counter — Design & Implementation Plan

## Goal

Build a high-throughput Spring Boot microservice that tracks how many users are actively viewing a given resource in real time. The service exposes two HTTP endpoints:

- `POST /v1/presence/heartbeat` — registers/refreshes a viewer's presence
- `GET /v1/resources/{resourceId}/viewers/count` — returns the live viewer count for a resource

---

## API Contract

### POST /v1/presence/heartbeat

**Request:**
```json
{
  "resourceId": "leetcode:123",
  "sessionId": "sess_abc123"
}
```

**Response:**
```json
{
  "resourceId": "leetcode:123",
  "sessionId": "sess_abc123",
  "viewerCount": 127,
  "expiresAt": "2026-09-13T12:00:30Z"
}
```

### GET /v1/resources/{resourceId}/viewers/count

**Response:**
```json
{
  "resourceId": "leetcode:123",
  "viewerCount": 127
}
```

> **Note:** `resourceId` values containing special characters (e.g., `leetcode:123`) must be URL-encoded in path variables (`leetcode%3A123`).

---

## Design Decisions

### ✅ Reactive Stack (Netty + WebFlux)

Using `spring-boot-starter-webflux` (backed by Netty + Project Reactor) instead of Spring MVC (Tomcat/Servlet). Each heartbeat is a tiny, fast Redis write — the thread-per-request model of Spring MVC is wasteful under heavy load. Reactive + Netty handles tens of thousands of concurrent heartbeats with a small event-loop thread pool.

### ✅ Redis ZSet as the Presence Store

Using a Redis Sorted Set (`ZSET`) keyed by `resource_tracker:{resourceId}`:

| Field  | Value                               |
|--------|-------------------------------------|
| Key    | `resource_tracker:{resourceId}`     |
| Member | `{sessionId}`                       |
| Score  | `expiresAt` (epoch milliseconds)    |

**Why this works well:**
- `ZADD` (upsert) is O(log N) — extremely fast for high-throughput heartbeats
- `ZCOUNT min max` counts active viewers in a time range in O(log N) time
- `ZREMRANGEBYSCORE` for the future cleanup job is equally efficient

### 💡 Score: `expiresAt` instead of `heartbeat_timestamp`

Rather than storing `heartbeat_timestamp` and querying `score > now - TTL`, we store `expiresAt = heartbeat_ts + SESSION_TTL` as the score. The active viewer query then becomes:

```
ZCOUNT resource_tracker:{resourceId}  {now_ms}  +inf
```

This is cleaner, maps directly to the `expiresAt` response field, and makes future cleanup trivial:

```
ZREMRANGEBYSCORE resource_tracker:{resourceId}  -inf  {now_ms}
```

### 💡 Key-level TTL (Auto-cleanup Safety Net)

In addition to the per-session TTL encoded in the score, we set a Redis key-level TTL (e.g. 24 hours) on the entire ZSET. If a resource receives no heartbeats for 24 hours the key is automatically evicted, preventing unbounded memory growth without requiring a cleanup job.

---

## Architecture

```
Client
  │
  ├── POST /v1/presence/heartbeat ──────────────────────────────────┐
  │                                                                  ▼
  │                                                    PresenceController
  │                                                          │
  │                                                    PresenceService
  │                                                          │
  │                                           ┌─────────────┴────────────┐
  │                                           ▼                          ▼
  │                                  ZADD (upsert session)      ZCOUNT (active viewers)
  │                                  EXPIRE (key-level TTL)
  │                                           │
  │                                      Redis ZSet
  │                               resource_tracker:{resourceId}
  │                               ┌──────────────────────────┐
  │                               │ member=sessionId         │
  │                               │ score=expiresAt (epoch)  │
  │                               └──────────────────────────┘
  │
  └── GET /v1/resources/{resourceId}/viewers/count ──────────────────┐
                                                                      ▼
                                                         PresenceController
                                                               │
                                                         PresenceService
                                                               │
                                                    ZCOUNT resource_tracker:{resourceId}
                                                           {now} +inf
```

---

## Package Structure

```
src/main/java/com/example/realtimeviewercounter/
├── RealtimeViewerCounterApplication.java
│
├── config/
│   └── RedisConfig.java               # ReactiveRedisTemplate<String, String> bean
│
├── controller/
│   └── PresenceController.java        # REST endpoints
│
├── dto/
│   ├── HeartbeatRequest.java          # POST body
│   ├── HeartbeatResponse.java         # POST response
│   └── ViewerCountResponse.java       # GET response
│
└── service/
    └── PresenceService.java           # Business logic + Redis operations
```

---

## Implementation Plan

### 1. Dependencies (`pom.xml`)

- **Replace** `spring-boot-starter-webmvc` → `spring-boot-starter-webflux` (Netty + Reactor)
- **Remove** `spring-boot-starter-websocket` (not needed for these endpoints)
- **Add** `spring-boot-starter-data-redis-reactive` (Lettuce-based async Redis client)
- **Add** `spring-boot-starter-validation` (`@NotBlank` on request DTOs)
- **Update** test dependencies to use `spring-boot-starter-test` + `reactor-test`

### 2. Configuration (`application.properties`)

```properties
spring.application.name=realtime-viewer-counter

# Redis connection
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Session TTL — how long after the last heartbeat a session is considered active
# Clients should heartbeat every 15-20s; default TTL is 30s
presence.session.ttl-seconds=30
```

### 3. Redis Config (`RedisConfig.java`)

Configure a `ReactiveRedisTemplate<String, String>` with `StringRedisSerializer` for both keys and values. Session IDs are strings; scores (doubles) are natively handled by Redis.

### 4. DTOs

| Class                  | Fields                                                 |
|------------------------|--------------------------------------------------------|
| `HeartbeatRequest`     | `@NotBlank resourceId`, `@NotBlank sessionId`          |
| `HeartbeatResponse`    | `resourceId`, `sessionId`, `viewerCount`, `expiresAt`  |
| `ViewerCountResponse`  | `resourceId`, `viewerCount`                            |

All DTOs implemented as Java 21 **records**.

### 5. Service (`PresenceService.java`)

| Operation        | Redis Command                                                       | Notes                       |
|------------------|---------------------------------------------------------------------|-----------------------------|
| Upsert session   | `ZADD resource_tracker:{id} {expiresAt_ms} {sessionId}`            | O(log N), idempotent upsert |
| Set key TTL      | `EXPIRE resource_tracker:{id} 86400`                               | 24h key-level auto-expiry   |
| Count active     | `ZCOUNT resource_tracker:{id} {now_ms} +inf`                       | O(log N)                    |

All operations return `Mono<T>` and are chained reactively.

### 6. Controller (`PresenceController.java`)

- `POST /v1/presence/heartbeat` — validates request body, delegates to `PresenceService.heartbeat()`
- `GET /v1/resources/{resourceId}/viewers/count` — delegates to `PresenceService.getViewerCount()`

Both handlers return `Mono<ResponseEntity<T>>`.

---

## Verification Plan

### Automated Tests

```bash
./mvnw test
```

- **Unit:** `PresenceService` with mocked `ReactiveRedisTemplate`, assertions via `StepVerifier`
- **Integration:** `PresenceController` using `WebTestClient` + Testcontainers Redis

### Manual Verification

```bash
# 1. Start Redis
docker run -p 6379:6379 redis

# 2. Start the service
./mvnw spring-boot:run

# 3. Send a heartbeat
curl -X POST http://localhost:8080/v1/presence/heartbeat \
  -H "Content-Type: application/json" \
  -d '{"resourceId":"leetcode:123","sessionId":"sess_abc123"}'

# 4. Get viewer count
curl http://localhost:8080/v1/resources/leetcode%3A123/viewers/count

# 5. Inspect Redis ZSet directly
redis-cli ZRANGE "resource_tracker:leetcode:123" 0 -1 WITHSCORES
```

---

## Out of Scope (Future Work)

- **Stale session cleanup job** — `@Scheduled` task running `ZREMRANGEBYSCORE key -inf now`. The key-level TTL provides a safety net in the interim.
- **Authentication / Authorization** — API key or JWT validation
- **Rate limiting** — Heartbeat endpoints are prime targets for abuse; Bucket4j or a Redis token-bucket can be added later
- **Horizontal scaling** — Redis is the shared state store, so the service is stateless and horizontally scalable out of the box
