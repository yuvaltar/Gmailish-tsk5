#include "server.h"
#include "SessionHandler.h"
#include <iostream>         // for std::cerr, std::cout
#include <sys/socket.h>     // for socket functions
#include <netinet/in.h>     // for sockaddr_in
#include <unistd.h>         // for close()
#include <string.h>         // for memset()
#include <thread>

// Constructor: initializes the server socket and BloomFilter reference

Server::Server(int port, BloomFilter& filter)
    : serverSocket(-1), bloomFilter(filter), running(true) {
    initSocket(port);  // Set up listening socket
}

// Destructor: calls shutdown to cleanly close the socket

Server::~Server() {
    shutdown();
}


// Gracefully shuts down the server socket
void Server::shutdown() {
    running = false;  // stop the run loop
    if (serverSocket != -1) {
        close(serverSocket);  // close the listening socket
        serverSocket = -1;
    }
}

// Sets up the TCP socket, binds it to the given port, and starts listening
void Server::initSocket(int port) {
    // Create a socket (IPv4, stream-based, TCP)

    serverSocket = socket(AF_INET, SOCK_STREAM, 0);
    if (serverSocket < 0) {
        perror("socket");  // Print error and exit if socket creation fails
        exit(1);
    }


    // Set up address structure for binding
    sockaddr_in sin;
    memset(&sin, 0, sizeof(sin));     // Zero out the structure
    sin.sin_family = AF_INET;         // IPv4
    sin.sin_addr.s_addr = INADDR_ANY; // Accept connections on any network interface
    sin.sin_port = htons(port);       // Convert port to network byte order


    // Bind the socket to the given port
    if (bind(serverSocket, (struct sockaddr*)&sin, sizeof(sin)) < 0) {
        perror("bind");
        exit(1);
    }

    // Listen for incoming connections (max 1 pending connection in backlog)
    if (listen(serverSocket, 50) < 0) {
        exit(1);
    }
}

// Main server loop: accepts and handles incoming client connections
void Server::run() {
    while (running) {
        sockaddr_in clientAddr;             // Client address info
        socklen_t clientLen = sizeof(clientAddr);

        // Accept a new client connection
        int clientSocket = accept(serverSocket, (sockaddr*)&clientAddr, &clientLen);

        if (clientSocket < 0) {
            if (!running) break;  // Exit gracefully if server is shutting down
            exit(1);
        }

        /// Launch a new thread for each client
        std::thread clientThread([this, clientSocket]() {
            this->handleClient(clientSocket);
        });

        clientThread.detach();  // Allow thread to run independently
    }
}

// Handles a single client session using SessionHandler
void Server::handleClient(int clientSocket) {
    // Construct a session handler with the accepted client socket and shared BloomFilter
    SessionHandler session(clientSocket, bloomFilter);
    session.handle();  // Process client commands
}
