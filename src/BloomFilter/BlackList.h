#ifndef BLACKLIST_H
#define BLACKLIST_H

#include <list>
#include <string>
#include <mutex>
#include "url.h"

// Class that manages a list of blacklisted URLs
class BlackList {
public:
    void addUrl(const URL& url);
    bool contains(const URL& url) const;
    void save(const std::string& path) const;
    void load(const std::string& path);
    void removeUrl(const URL& url);

private:
    std::list<URL> blacklist;     // Container holding the blacklisted URLs
    mutable std::mutex mtx;       // Mutex to guard blacklist access
};

#endif // BLACKLIST_H
