// BloomFilter.cpp
#include "BloomFilter.h"                   // Include BloomFilter class definition
#include <fstream>                          // For file I/O operations
#include <iostream>



// Constructor: initializes size, bit array, and hash functions
BloomFilter::BloomFilter(size_t size, const std::vector<std::shared_ptr<IHashFunction>>& hashFunctions)
    : m_size(size), bitArray(size, false), hashFunctions(hashFunctions) {}

//Add a URL to the Bloom filter by hashing and setting bits
void BloomFilter::add(const URL& item) {
    for (const auto& hf : hashFunctions) {                         // For each hash function
        size_t hashValue = hf->hash(item.getURL());               // Hash the URL
        size_t index = hashValue % m_size;                        // Map to index in bit array
        bitArray[index] = true;                                   // Set bit to true
    }
}


// Check if a URL might be in the Bloom filter
bool BloomFilter::possiblyContains(const URL& item) const {
    for (const auto& hf : hashFunctions) {                         // For each hash function
        size_t hashValue = hf->hash(item.getURL());               // Hash the URL
        size_t index = hashValue % m_size;                        // Map to index
        if (!bitArray[index]) return false;                       // If any bit is false, it's not in
    }
    return true;                                                  // Otherwise, possibly in set
}

// Get the internal bit array (for external use or testing)
const std::vector<bool>& BloomFilter::getBitArray() const {
    return bitArray;
}

// Set the bit array (used when loading from file)
void BloomFilter::setBitArray(const std::vector<bool>& bits) {
    if (bits.size() == m_size) {                                   // Check size match
        bitArray = bits;                                          // Replace internal bit array
    }
}

// Save the bit array to a binary file
void BloomFilter::saveToFile(const std::string& path) const {
    std::ofstream out(path, std::ios::binary);                    // Open file in binary write mode
    for (bool bit : bitArray) {                                   // Write each bit
        out.write(reinterpret_cast<const char*>(&bit), sizeof(bool));
    }
}

// Load bit array from binary file and update internal array
void BloomFilter::loadFromFile(const std::string& path) {
    std::ifstream in(path, std::ios::binary);                     // Open file in binary read mode
    std::vector<bool> loadedBits;                                 // Temporary container
    bool bit;
    while (in.read(reinterpret_cast<char*>(&bit), sizeof(bool))) { // Read each bit
        loadedBits.push_back(bit);                                // Store in vector
    }
    setBitArray(loadedBits);                                      // Set internal bit array
}

