#include "url.h"                           // Include the header for the URL class

// Constructor: initialize the URL with the given string
URL::URL(const std::string& url) : url(url) {}

// Getter: returns the stored URL string
std::string URL::getURL() const {
    return url;
}

// Equality operator: compares two URL objects based on their internal strings
bool URL::operator==(const URL& other) const {
    return url == other.url;
}
