// BloomFilter.h
#ifndef BLOOMFILTER_H         // Include guard start: prevents multiple inclusions
#define BLOOMFILTER_H

#include <vector>             // For std::vector (used in bit array and hash function list)
#include <memory>             // For std::shared_ptr (used for hash functions)
#include <string>             // For std::string (used in file path)
#include "IHashFunctions.h"   // Interface for hash functions
#include "url.h"              // URL class definition

// BloomFilter class definition
class BloomFilter {
public:
    // Constructor: initializes Bloom filter with size and list of hash functions
    BloomFilter(size_t size, const std::vector<std::shared_ptr<IHashFunction>>& hashFunctions);

    // Adds a URL to the Bloom filter by setting bits based on hash values
    void add(const URL& item);

    // Checks whether a URL might be in the filter (could be false positive)
    bool possiblyContains(const URL& item) const;

    // Returns a const reference to the bit array (used for testing/debugging)
    const std::vector<bool>& getBitArray() const;

    // Sets the entire bit array to a new value (used in tests or restoring from file)
    void setBitArray(const std::vector<bool>& bits);

    // Saves the bit array to a binary file for persistence
    void saveToFile(const std::string& path) const;

    // Loads the bit array from a binary file
    void loadFromFile(const std::string& path);

private:
    size_t m_size;                                         // Total number of bits in the filter
    std::vector<bool> bitArray;                            // Bit array representing the filter
    std::vector<std::shared_ptr<IHashFunction>> hashFunctions; // List of hash functions to use
};

#endif // BLOOMFILTER_H        // End of include guard
