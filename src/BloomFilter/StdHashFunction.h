#ifndef STDHASHFUNCTION_H                 // Prevent multiple includes of header
#define STDHASHFUNCTION_H

#include "IHashFunctions.h"              // Interface for hash function
#include <functional>                    // For std::hash
#include <string>                        // For std::string

class StdHashFunction : public IHashFunction {
public:
    explicit StdHashFunction(int iterations) : iterations_(iterations) {}  // Constructor sets iteration count

    size_t hash(const std::string& input) const override {  // Override base class method
        std::hash<std::string> hasher;       // Standard hash function object
        std::string current = input;         // Copy of input to modify each iteration
        size_t hashValue = 0;                // Store intermediate hash result

        for (int i = 0; i < iterations_; ++i) {  // Repeat hash multiple times
            hashValue = hasher(current);     // Hash current string
            current = std::to_string(hashValue); // Convert hash to string for next round
        }

        return hashValue;                    // Return final hash result
    }

private:
    int iterations_;                         // Number of iterations to apply hashing
};

#endif // STDHASHFUNCTION_H
