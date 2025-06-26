#pragma once

#include <string>
#include "BloomFilter.h"
#include "IHashFunctions.h"
#include "StdHashFunction.h"
#include "CommandManager.h"

// Handles interaction with a single client
class SessionHandler {
public:
    SessionHandler(int socket, BloomFilter& sharedFilter);  // Now accepts BloomFilter

    void handle();

private:
    int clientSocket;                      // Client socket
    BloomFilter& bloom;                    // Copy of server's BloomFilter (per session)

    std::string receiveLine();            // Read command from client
    void sendResponse(const std::string& response);  // Send response
    void closeConnection();               // Optional cleanup helper
};
