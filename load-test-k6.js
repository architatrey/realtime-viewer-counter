import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';

const loadLevel = __ENV.LOAD_LEVEL || 'low';

// Define our load profiles using k6 "stages" for variable users (ramp-up, peak, ramp-down)
const profiles = {
  low: [
      { duration: '10s', target: 10 }, // Ramp-up to 10 users
      { duration: '30s', target: 10 }, // Stay at 10 users
      { duration: '10s', target: 0 },  // Ramp-down to 0
  ],
  medium: [
      { duration: '20s', target: 200 }, // Ramp-up to 200 users
      { duration: '1m', target: 200 },  // Stay at 200 users
      { duration: '20s', target: 0 },   // Ramp-down to 0
  ],
  high: [
      { duration: '30s', target: 2000 },// Ramp-up to 2000 users
      { duration: '2m', target: 2000 }, // Stay at 2000 users
      { duration: '30s', target: 0 },   // Ramp-down to 0
  ],
};

// Export the selected stages for k6 to use
export const options = {
    stages: profiles[loadLevel] || profiles['low']
};

// Multiple resources to simulate different streams
const RESOURCES = [
    'stream-alpha', 
    'stream-beta', 
    'stream-gamma', 
    'stream-delta', 
    'stream-omega'
];

export default function () {
  const url = 'http://localhost:8080/v1/presence/heartbeat';
  
  // Pick a random resource for this user
  const randomResource = RESOURCES[Math.floor(Math.random() * RESOURCES.length)];
  
  // Create a unique session ID for this Virtual User
  const sessionId = `session-${exec.vu.idInTest}-${exec.vu.iterationInInstance}`;

  const payload = JSON.stringify({
    resourceId: randomResource,
    sessionId: sessionId,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const res = http.post(url, payload, params);
  
  check(res, {
    'is status 200': (r) => r.status === 200,
  });

  // Clients typically send a heartbeat every 5 seconds.
  sleep(5);
}
