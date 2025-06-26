#include "CommandManager.h"
#include "url.h"
#include <sstream>  // For parsing input
#include <regex>    // For validating URL format
#include <iostream> // For debugging (not actively used)

// A regex pattern to validate URL formats
static const std::regex urlRegex(
    R"(^(?:(?:file:///(?:[A-Za-z]:)?(?:/[^\s]*)?)|(?:(?:[A-Za-z][A-Za-z0-9+.-]*)://)?(?:localhost|(?:[A-Za-z0-9-]+\.)+[A-Za-z0-9-]+|(?:\d{1,3}\.){3}\d{1,3})(?::\d+)?(?:/[^\s]*)?)$)");


// Constructor: initializes references to the shared BloomFilter and BlackList
CommandManager::CommandManager(BloomFilter& bloom, BlackList& blacklist)
    : bloom(bloom), blacklist(blacklist) {}

// Executes a single command line: expects format "COMMAND URL"
std::string CommandManager::execute(const std::string& commandLine) {
    std::istringstream iss(commandLine);
    std::string command, urlStr;

    // Extract command and URL; reject malformed input

    if (!(iss >> command >> urlStr)) {
        return "400 Bad Request\n";
    }
  
    // Ensure there are no extra tokens after the URL

    std::string extra;
    if (iss >> extra) {
        return "400 Bad Request\n";
    }

    // Validate URL format using regex
    if (!std::regex_match(urlStr, urlRegex)) {
        return "400 Bad Request\n";
    }


    // Construct a URL object for further processing
    URL url(urlStr);

    // Handle POST command: add to blacklist and Bloom filter if not already present
    if (command == "POST") {
        if (!blacklist.contains(url)) {
            blacklist.addUrl(url);   // Add to exact list
            bloom.add(url);          // Add to probabilistic filter
        }
        return "201 Created\n";        // Success response
    }

    // Handle DELETE command: remove from blacklist if it exists
    else if (command == "DELETE") {
        if (blacklist.contains(url)) {
            blacklist.removeUrl(url);  // Remove from blacklist
            return "204 No Content\n";   // Success, no content
        } else {
            return "404 Not Found\n";    // Cannot remove nonexistent entry
        }
    }

    // Handle GET command: report presence in Bloom filter and blacklist
    else if (command == "GET") {
        std::string response = "200 Ok\n\n";

        bool inBloom = bloom.possiblyContains(url);
        response += (inBloom ? "true " : "false ");

        if (inBloom) {
            // Only check blacklist if Bloom thinks it's present
            response += (blacklist.contains(url) ? "true" : "false");
        }

        return response + "\n";
    }

    // If command is unrecognized, return an error
    return "400 Bad Request\n";
}
