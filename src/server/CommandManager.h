#pragma once
#include <string>
#include "BloomFilter.h"
#include "BlackList.h"

// CommandManager is responsible for interpreting and executing server-side logic.
// It receives raw (validated or unvalidated) commands and returns appropriate responses.
class CommandManager {
public:
    // Constructor: takes references to the shared BloomFilter and BlackList.
    CommandManager(BloomFilter& bloom, BlackList& blacklist);

    // Receives a command line and returns an HTTP-style response string.
    // Expected commands: POST <url>, GET <url>, DELETE <url>
    std::string execute(const std::string& commandLine);

private:
    BloomFilter& bloom;    // Reference to shared Bloom filter
    BlackList& blacklist;  // Reference to shared blacklist
};
