// IHash function interface
#ifndef IHASHFUNCTION_H        // Include guard start: prevents multiple inclusion
#define IHASHFUNCTION_H

#include <string>              // For std::string, used in the hash function parameter

// Interface class for hash functions
class IHashFunction {
public:
    virtual ~IHashFunction() {}                      // Virtual destructor for proper cleanup in derived classes
    virtual size_t hash(const std::string &input) const = 0; // Pure virtual function to hash a string
};

#endif // IHASHFUNCTION_H       // End of include guard
