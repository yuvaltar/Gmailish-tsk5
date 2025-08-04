#ifndef URL_H                              // Start of include guard to prevent multiple inclusion
#define URL_H

#include <string>                          // For std::string (used to store the URL)
#include <vector>                          // For std::vector (used to return multiple hashes)
#include <functional>                      // For std::hash (used for hashing strings)

// Class that wraps a URL string and supports hashing and comparison
class URL {
private:
    std::string url;                       // The URL string

public:
    // Constructor: initializes the URL with a given string
    URL(const std::string& url);

    // Getter function: returns the stored URL string
    std::string getURL() const;

    // Hash the URL using a vector of hash functions
    std::vector<size_t> hash(const std::vector<std::hash<std::string>>& hash_functions) const;

    // Equality operator: checks if two URLs are equal based on their strings
    bool operator==(const URL& other) const;
};

#endif // URL_H                             // End of include guard
