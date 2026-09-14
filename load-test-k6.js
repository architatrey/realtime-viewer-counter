import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';

// Read the LOAD_LEVEL environment variable, default to 'low'
const loadLevel = __ENV.LOAD_LEVEL || 'low';

// Define our load profiles
const profiles = {
  low: { 
    vus: 10, 
    duration: '30s' 
  },
  medium: { 
    vus: 200, 
    duration: '1m' 
  },
  high: { 
    vus: 2000, 
    duration: '2m' 
  },
};

// Export the selected options for k6 to use
export const options = profiles[loadLevel] || profiles['low'];

export default function () {
  const url = 'http://localhost:8080/v1/presence/heartbeat';
  
  // Create a unique session ID for this Virtual User
  const sessionId = `session-${exec.vu.idInTest}-${exec.vu.iterationInInstance}`;

  const payload = JSON.stringify({
    resourceId: 'test-stream-123',
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
