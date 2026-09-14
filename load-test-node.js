const http = require('http');

// Read load level from command line arguments, default to 'low'
const loadLevel = (process.argv[2] || 'low').toLowerCase();

// Define our load profiles (number of concurrent virtual users)
const profiles = {
  low: 10,
  medium: 200,
  high: 2000,
};

// CONFIGURATION
const VIRTUAL_USERS = profiles[loadLevel] || profiles.low;
const RESOURCE_ID = 'test-stream-123';
const HEARTBEAT_INTERVAL_MS = 5000; 

function sendHeartbeat(sessionId) {
  return new Promise((resolve) => {
    const data = JSON.stringify({
      resourceId: RESOURCE_ID,
      sessionId: sessionId
    });

    const options = {
      hostname: 'localhost',
      port: 8080,
      path: '/v1/presence/heartbeat',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(data)
      }
    };

    const req = http.request(options, (res) => {
      res.on('data', () => {}); 
      res.on('end', () => resolve(res.statusCode));
    });

    req.on('error', (e) => {
      resolve(e.message);
    });

    req.write(data);
    req.end();
  });
}

async function startLoadTest() {
  console.log(`=== Starting '${loadLevel.toUpperCase()}' Load Test ===`);
  console.log(`Targeting: http://localhost:8080/v1/presence/heartbeat`);
  console.log(`Simulating ${VIRTUAL_USERS} concurrent users sending heartbeats every ${HEARTBEAT_INTERVAL_MS}ms.\n`);
  
  // Generate a list of unique session IDs
  const users = Array.from({ length: VIRTUAL_USERS }, (_, i) => `user-${i}-${Date.now()}`);

  // Send a batch of heartbeats every interval
  setInterval(async () => {
      const startTime = Date.now();
      let successCount = 0;
      let errorCount = 0;

      // Map all users to HTTP request promises
      const promises = users.map(async (sessionId) => {
         const status = await sendHeartbeat(sessionId);
         if (status === 200) {
            successCount++;
         } else {
            errorCount++;
         }
      });

      // Wait for all heartbeats in this batch to finish
      await Promise.all(promises);
      
      const duration = Date.now() - startTime;
      console.log(`[${new Date().toISOString()}] Sent ${VIRTUAL_USERS} heartbeats in ${duration}ms. Success: ${successCount}, Errors: ${errorCount}`);
      
  }, HEARTBEAT_INTERVAL_MS);
}

startLoadTest();
