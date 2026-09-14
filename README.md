# Realtime Viewer Counter

A project to count viewers in real-time.

## Running the Application

Before running load tests, ensure the backend and Redis are running:

```bash
# 1. Start Redis in the background
docker-compose up -d

# 2. Start the Spring Boot Application
./mvnw spring-boot:run
```

## Load Testing

You can simulate bulk load (viewers sending regular heartbeats) using either `k6` or `Node.js`. Both scripts support three load profiles:
- **low**: 10 concurrent users
- **medium**: 200 concurrent users
- **high**: 2,000 concurrent users

### Option 1: k6 (Recommended)
Make sure you have [k6 installed](https://k6.io/) (e.g., `brew install k6`). 
Pass the `LOAD_LEVEL` environment variable to choose the profile:

```bash
# Default (low)
k6 run load-test-k6.js

# Medium
k6 run -e LOAD_LEVEL=medium load-test-k6.js

# High
k6 run -e LOAD_LEVEL=high load-test-k6.js
```

### Option 2: Node.js
If you prefer not to install extra tooling, use the vanilla Node.js script. Pass the profile as an argument:

```bash
# Default (low)
node load-test-node.js

# Medium
node load-test-node.js medium

# High
node load-test-node.js high
```
