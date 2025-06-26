#include "BlackList.h"
#include <fstream>
#include <algorithm>

// Add a URL to the blacklist
void BlackList::addUrl(const URL& url) {
    std::lock_guard<std::mutex> lock(mtx);
    blacklist.push_back(url);
}

// Check if the blacklist contains the given URL
bool BlackList::contains(const URL& url) const {
    std::lock_guard<std::mutex> lock(mtx);
    return std::find(blacklist.begin(), blacklist.end(), url) != blacklist.end();
}

// Save all URLs in the blacklist to a file, one per line
void BlackList::save(const std::string& path) const {
    std::lock_guard<std::mutex> lock(mtx);
    std::ofstream out(path);
    for (const auto& url : blacklist) {
        out << url.getURL() << "\n";
    }
}

// Load URLs from a file and add them to the blacklist
void BlackList::load(const std::string& path) {
    std::lock_guard<std::mutex> lock(mtx);
    std::ifstream in(path);
    std::string line;
    while (std::getline(in, line)) {
        if (!line.empty()) {
            blacklist.emplace_back(line);
        }
    }
}

// Remove a URL from the blacklist
void BlackList::removeUrl(const URL& url) {
    std::lock_guard<std::mutex> lock(mtx);
    blacklist.remove(url);
}
