#pragma once
#include "BloomFilter.h"
#include "BlackList.h"
#include "CommandManager.h"
#include <atomic>

// The Server class owns the listening socket.
// It handles initialization, accepting clients, and delegating each connection to a handler.
class Server {
public:
    // Constructor: initializes server with port only (per-client filters are handled in sessions).
    
    Server(int port, BloomFilter& bloomFilter);
    ~Server();  // Destructor
    // Starts the server: binds the socket and begins accepting client connections.
    void run();
    void shutdown();  //

private:
    int serverSocket;                 // Listening socket
    BloomFilter& bloomFilter;
     std::atomic<bool> running;  // Controls server loop
    // Initializes the server socket: bind, listen, etc.
    void initSocket(int port);

    // Handles communication with a single connected client
    void handleClient(int clientSocket);
};
