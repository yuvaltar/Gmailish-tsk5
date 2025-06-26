#include "SessionHandler.h"
#include <unistd.h>             // for close() and read/write syscalls
#include <sys/socket.h>         // for socket-related functions (recv, send)
#include <iostream>             // for std::cerr (if needed for debugging)
#include <cstring>              // for std::strerror (not used here but commonly needed)
#include <sstream>              // for parsing input (not used here)
#include <filesystem>           // for file/directory manipulation
#include "BlackList.h"          // blacklist for exact URL storage
#include "CommandManager.h"     // handles command parsing and execution

// Constructor: store the client socket and reference to shared BloomFilter

SessionHandler::SessionHandler(int socket, BloomFilter& sharedFilter)
    : clientSocket(socket), bloom(sharedFilter) {}  // Copy the shared BloomFilter config reference


// Reads a single line (terminated by '\n') from the client socket
std::string SessionHandler::receiveLine() {
    std::string line;
    char ch;

    while (true) {
        // Read one byte at a time from socket

        ssize_t bytesRead = recv(clientSocket, &ch, 1, 0);

        if (bytesRead == 1) {
            line += ch;
            if (ch == '\n') break;  // End of line
        } else if (bytesRead == 0) {
            return "";  // Connection closed by client
        } else {
            return "";  // Error occurred
        }
    }

    // Remove trailing newline or carriage return characters
    while (!line.empty() && (line.back() == '\n' || line.back() == '\r')) {
        line.pop_back();
    }

    return line;
}

// Sends a response string back to the client

void SessionHandler::sendResponse(const std::string& response) {
    size_t totalSent = 0;
    size_t toSend = response.size();
    const char* buffer = response.c_str();

    // Send loop to ensure all bytes are transmitted
    while (totalSent < toSend) {
        // Send remaining portion of the response
        ssize_t sent = send(clientSocket, buffer + totalSent, toSend - totalSent, 0);
        if (sent == -1) {
            // Send error (likely connection broken)
            break;
        }
        totalSent += sent;
    }
}

// Main session handler for one client connection
void SessionHandler::handle() {
    // Ensure the data directory exists (used for persistence files)
    std::filesystem::create_directory("data");

    // Fixed shared persistence file paths (used across all sessions)
    std::string BloomFile = "data/bloom_shared.bin";
    std::string BlackListFile = "data/blacklist_shared.txt";

    // Create a BlackList object to hold exact blacklisted URLs

    BlackList blacklist;

    // Load existing Bloom filter and blacklist from persistent files
    bloom.loadFromFile(BloomFile);
    blacklist.load(BlackListFile);

    // CommandManager handles interpreting and executing client commands
    CommandManager commandManager(bloom, blacklist);

    // Main session loop: process incoming commands from the client

    while (true) {
        std::string command = receiveLine();  // Read client command line

        if (command.empty()) {

            // Client disconnected or sent an empty line
            break;
        }

        // Process the command and get the server's response
        std::string response = commandManager.execute(command);

        // Send the result back to the client
        sendResponse(response);

        // Persist the updated Bloom filter and blacklist to files
        bloom.saveToFile(BloomFile);
        blacklist.save(BlackListFile);
    }

    // Close the socket once the session ends

    close(clientSocket);
}
