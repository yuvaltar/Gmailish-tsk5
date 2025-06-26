// server/services/blacklistService.js
const net = require('net');

// Use environment variable if set, otherwise default to 4000
const CPP_PORT = process.env.CPP_PORT || 4000;

// Host for the C++ BloomFilter service
const CPP_HOST = process.env.CPP_HOST || '127.0.0.1';

/**
 * Sends a command to the C++ BloomFilter server and returns the response.
 * @param {string} command - The command string (e.g., 'POST <url>', 'GET <url>')
 * @returns {Promise<string>} - Resolves with the server's response.
 */
async function sendToCpp(command) {
  return new Promise((resolve, reject) => {
    const client = new net.Socket();

    // Connect to the C++ service
    client.connect(CPP_PORT, CPP_HOST, () => {
      client.write(command + '\n');
    });

    client.on('data', (data) => {
      resolve(data.toString());
      client.destroy(); // close connection
    });

    client.on('error', (err) => {
      reject(`Connection error: ${err.message}`);
    });
  });
}

module.exports = { sendToCpp };
