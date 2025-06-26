#include <iostream>          // For standard I/O operations
#include <sstream>           // For string stream processing
#include <string>            // For std::string
#include <vector>            // For std::vector
#include <memory>            // For smart pointers like std::unique_ptr and std::shared_ptr
#include <list>              // For std::list
#include <algorithm>         // For std::find and other algorithms
#include <fstream>           // For file read/write operations
#include "BlackList.h"       // Include BlackList class
#include "BloomFilter.h"     // Include BloomFilter class
#include "url.h"             // Include URL class
#include "IHashFunctions.h"  // Include interface for hash functions
#include "StdHashFunction.h" // Include standard hash function
#include <gtest/gtest.h>     // Google Test framework

int g_bit_size = 816;        // Global bit size for Bloom filter
int g_hash_func1 = 17;       // Global ID/iteration count for first hash function
int g_hash_func2 = 5;        // Global ID/iteration count for second hash function

std::unique_ptr<BloomFilter> bf;  // Global unique pointer to a BloomFilter instance
BlackList blackList;              // Global instance of BlackList

// ---------- Valid Input Check ------------
// Check that bit size and hash function IDs are valid.
TEST(ValidationTest, InvalidInputValues) {
    EXPECT_TRUE(g_bit_size % 8 == 0);      // Ensure bit size is multiple of 8
    EXPECT_GT(g_bit_size, 0);              // Ensure bit size is positive
    EXPECT_GE(g_hash_func1, 0);            // Ensure hash function 1 ID is non-negative
    EXPECT_GE(g_hash_func2, 0);            // Ensure hash function 2 ID is non-negative
}

// ---------- Regex-Free HTTP Check ------------
// Check that a URL starts with "http://" or "https://".
TEST(URLTest, URLStartsWithHttp) {
    std::string url = "http://example.com";                        // Define a test URL
    bool isValid = url.rfind("http://", 0) == 0 || url.rfind("https://", 0) == 0; // Check prefix
    ASSERT_TRUE(isValid);                                          // Assert it starts correctly
}

// ---------- Inserting URLs to BloomFilter and BlackList Tests ------------

// Add a URL to both BloomFilter and BlackList and verify they contain it.
TEST(URLTest, AddURLToBloomFilter) {
    URL url("http://example.com");         // Create URL instance
    bf->add(url);                          // Add to Bloom filter
    blackList.addUrl(url);                 // Add to BlackList
    ASSERT_TRUE(bf->possiblyContains(url)); // Check Bloom filter reports it may contain it
    ASSERT_TRUE(blackList.contains(url));   // Check BlackList definitely contains it
}

// Add one URL, test against another. Check BloomFilter may be positive but BlackList must be false.
TEST(BloomFilterIntegration, URLInListShouldMatch) {
    URL url("http://example.com");         // URL to add
    URL url2("http://example2.com");       // URL to check that wasn't added
    bf->add(url);                          // Add first URL to Bloom filter
    blackList.addUrl(url);                 // Add first URL to BlackList

    if (bf->possiblyContains(url2)) {      // If Bloom filter says url2 is present
        ASSERT_FALSE(blackList.contains(url2)); // BlackList must say it’s not
    }
}

// Add many URLs, check target URL is found and unknown URL is not.
TEST(BloomFilterIntegration, MultipleURLsInListShouldFindTarget) {
    bf->setBitArray(std::vector<bool>(g_bit_size, false)); // Reset Bloom filter to all 0
    blackList = BlackList();                               // Reset BlackList

    std::vector<std::string> urls = {                      // List of URLs to add
        "http://a.com", "http://b.com", "http://c.com", "http://d.com",
        "http://e.com", "http://f.com", "http://g.com", "http://example.com"
    };

    for (const auto& u : urls) {                           // Loop through each URL
        bf->add(URL(u));                                   // Add to Bloom filter
        blackList.addUrl(URL(u));                          // Add to BlackList
    }

    ASSERT_TRUE(bf->possiblyContains(URL("http://example.com"))); // Should exist
    ASSERT_TRUE(blackList.contains(URL("http://example.com")));   // Should exist

    URL notInList("http://not-in-list.com");               // URL not added
    if (bf->possiblyContains(notInList)) {                 // If Bloom says maybe
        ASSERT_FALSE(blackList.contains(notInList));       // BlackList says definitely no
    } else {
        ASSERT_FALSE(blackList.contains(notInList));       // Also should not be in BlackList
    }
}

// ---------- Persistence Tests ------------

// Save and load BlackList, check that added URL persists.
TEST(PersistenceTest, BlackListURLFilePersistence) {
    BlackList bl;                                   // Create new BlackList
    URL url("http://blacklisted.com");              // URL to add
    bl.addUrl(url);                                 // Add URL
    bl.save("data/test_blacklist.txt");             // Save to file

    BlackList loaded;                               // Create new BlackList
    loaded.load("data/test_blacklist.txt");         // Load from file
    EXPECT_TRUE(loaded.contains(url));              // Confirm URL persisted
}

// Save and load BloomFilter, check that added URL persists.
TEST(PersistenceTest, BloomFilterFilePersistence) {
    URL url("http://bloomfilter.com");              // URL to add

    BloomFilter bf(g_bit_size, {                    // Create BloomFilter
        std::make_shared<StdHashFunction>(g_hash_func1),
        std::make_shared<StdHashFunction>(g_hash_func2)
    });

    bf.add(url);                                    // Add URL to BloomFilter
    bf.saveToFile("data/test_bloomfilter.bin");     // Save Bloom filter to file

    BloomFilter loaded(g_bit_size, {                // Create new BloomFilter
        std::make_shared<StdHashFunction>(g_hash_func1),
        std::make_shared<StdHashFunction>(g_hash_func2)
    });

    loaded.loadFromFile("data/test_bloomfilter.bin"); // Load from file
    EXPECT_TRUE(loaded.possiblyContains(url));        // Confirm URL persisted
}

// ---------- Bit Array Edge Tests ----------

// Test that when all BloomFilter bits are 0, no match occurs.
TEST(BloomFilterEdgeCase, AllZerosBitArrayShouldReturnFalse) {
    bf->setBitArray(std::vector<bool>(g_bit_size, false)); // Reset all bits to 0
    URL url("http://neveradded.com");                      // URL not added
    EXPECT_FALSE(bf->possiblyContains(url));               // Expect Bloom says no
}

// Test that when all BloomFilter bits are 1, any URL may return true.
TEST(BloomFilterEdgeCase, AllOnesBitArrayShouldReturnTrue) {
    bf->setBitArray(std::vector<bool>(g_bit_size, true));  // Set all bits to 1
    URL url("http://neveradded.com");                      // Random URL
    EXPECT_TRUE(bf->possiblyContains(url));                // Expect Bloom says yes (false positive)
}

// ---------- Integration Test ------------
// Save and reload both BloomFilter and BlackList, verify persistence works and false positives are handled.
TEST(IntegrationTest, BlacklistAndBloomFilterPersistence) {
    size_t filterSize = 256;                                // Set a smaller filter size for test

    std::vector<std::shared_ptr<IHashFunction>> hashFunctions = { // Setup hash functions
        std::make_shared<StdHashFunction>(g_hash_func1),
        std::make_shared<StdHashFunction>(g_hash_func2)
    };

    BloomFilter bf(filterSize, hashFunctions);              // Create BloomFilter
    BlackList bl;                                           // Create BlackList

    URL goodUrl("http://safe-site.com");                    // Not blacklisted
    URL badUrl("http://evil-site.com");                     // Will be blacklisted

    bf.add(badUrl);                                         // Add bad URL to Bloom
    bl.addUrl(badUrl);                                      // Add bad URL to BlackList

    std::string bloomFile = "data/integration_bloom.bin";   // Bloom filter file
    std::string blacklistFile = "data/integration_blacklist.txt"; // BlackList file

    bf.saveToFile(bloomFile);                               // Save Bloom filter
    bl.save(blacklistFile);                                 // Save BlackList

    BloomFilter bfLoaded(filterSize, hashFunctions);        // Load BloomFilter
    bfLoaded.loadFromFile(bloomFile);

    BlackList blLoaded;                                     // Load BlackList
    blLoaded.load(blacklistFile);

    ASSERT_TRUE(bfLoaded.possiblyContains(badUrl));         // Should contain bad URL
    ASSERT_TRUE(blLoaded.contains(badUrl));                 // Should contain bad URL
    ASSERT_FALSE(blLoaded.contains(goodUrl));               // Should not contain good URL

    if (!bfLoaded.possiblyContains(goodUrl)) {              // If Bloom says good URL is not there
        SUCCEED();                                          // Test passes
    } else {
        std::cout << "[ WARNING ] Bloom filter false positive for: " << goodUrl.getURL() << "\n"; // Warn
    }
}

// ---------- Main Function ------------
// Initializes Google Test and sets up a default BloomFilter instance for shared use.
int main(int argc, char* argv[]) {
    std::vector<std::shared_ptr<IHashFunction>> hashFuncs = { // Create shared hash functions
        std::make_shared<StdHashFunction>(g_hash_func1),
        std::make_shared<StdHashFunction>(g_hash_func2)
    };

    bf = std::make_unique<BloomFilter>(g_bit_size, hashFuncs); // Initialize global BloomFilter

    ::testing::InitGoogleTest(&argc, argv);                    // Initialize Google Test
    return RUN_ALL_TESTS();                                    // Run all tests and return result
}
