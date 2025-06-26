#include "server.h"
#include <iostream>
#include <vector>
#include <memory>
#include <cmath>
#include <cstdlib>
#include "StdHashFunction.h"
#include "BloomFilter.h"

// Check if power of 2
bool isPowerOfTwo(int n) {
    return n > 0 && (n & (n - 1)) == 0;
}

int main(int argc, char* argv[]) {
    // Default values
    int port = 4000;
    int filterSize = 1024;
    std::vector<int> hashIterations = {3, 5};

    // Override if args provided
    if (argc >= 4) {
        try {
            port = std::stoi(argv[1]);
            filterSize = std::stoi(argv[2]);

            hashIterations.clear();
            for (int i = 3; i < argc; ++i) {
                int iter = std::stoi(argv[i]);
                if (iter <= 0) throw std::invalid_argument("Non-positive hash iter");
                hashIterations.push_back(iter);
            }
        } catch (...) {
            std::cerr << "Invalid arguments.\n";
            return 1;
        }
    }

    if (port <= 1024 || port > 65535 || filterSize <= 0 || !isPowerOfTwo(filterSize) || hashIterations.empty()) {
        std::cerr << "Invalid configuration.\n";
        return 1;
    }

    std::vector<std::shared_ptr<IHashFunction>> hashFns;
    for (int iter : hashIterations)
        hashFns.push_back(std::make_shared<StdHashFunction>(iter));

    BloomFilter bloom(filterSize, hashFns);
    std::cout << "Starting server on port " << port << std::endl;
    Server server(port, bloom);
    server.run();

    return 0;
}
