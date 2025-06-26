#include <gtest/gtest.h>
#include <thread>
#include <chrono>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <unistd.h>
#include <cstring>
#include <memory>
#include <filesystem>
#include "server.h"
#include "StdHashFunction.h"
#include "IHashFunctions.h"
#include "BloomFilter.h"

constexpr int TEST_PORT = 54321;
const std::string BLOOM_FILE = "data/bloom_shared.bin";
const std::string BLACKLIST_FILE = "data/blacklist_shared.txt";

Server* globalTestServer = nullptr;
std::thread serverThread;

// ===============================
// Utility: Clean shared state
// ===============================
void cleanPersistenceFiles() {
    std::filesystem::remove(BLOOM_FILE);
    std::filesystem::remove(BLACKLIST_FILE);
}

// ===============================
// Utility: Start server
// ===============================
void startServer() {
    std::vector<std::shared_ptr<IHashFunction>> hashFns = {
        std::make_shared<StdHashFunction>(3),
        std::make_shared<StdHashFunction>(5)
    };
    BloomFilter* bloom = new BloomFilter(512, hashFns);
    globalTestServer = new Server(TEST_PORT, *bloom);
    serverThread = std::thread([]() {
        globalTestServer->run();
    });
    std::this_thread::sleep_for(std::chrono::milliseconds(200));
}

// ===============================
// Utility: Stop server
// ===============================
void stopServer() {
    globalTestServer->shutdown();
    serverThread.join();
    delete globalTestServer;
    globalTestServer = nullptr;
}

// ===============================
// Utility: Send command to server
// ===============================
std::string sendCommandToServer(const std::string& command, int port = TEST_PORT) {
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) return "socket_error";

    sockaddr_in serverAddr;
    std::memset(&serverAddr, 0, sizeof(serverAddr));
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_port = htons(port);
    inet_pton(AF_INET, "127.0.0.1", &serverAddr.sin_addr);

    if (connect(sock, (sockaddr*)&serverAddr, sizeof(serverAddr)) < 0) {
        close(sock);
        return "connect_error";
    }

    send(sock, command.c_str(), command.size(), 0);
    char buffer[4096] = {};
    int bytesRead = recv(sock, buffer, sizeof(buffer) - 1, 0);
    close(sock);
    return (bytesRead > 0) ? std::string(buffer, bytesRead) : "recv_error";
}

// ===============================
// Stateless Tests
// ===============================
class ServerClientTest : public ::testing::Test {
protected:
    void SetUp() override {
        cleanPersistenceFiles();  // ensure stateless
    }
};

TEST_F(ServerClientTest, PostCommandRoundTrip) {
    std::string response = sendCommandToServer("POST http://example.com\n");
    EXPECT_EQ(response, "201 Created");
}

TEST_F(ServerClientTest, InvalidFormatCommandReturns400) {
    std::string response = sendCommandToServer("INVALIDCMD something\n");
    EXPECT_EQ(response, "400 Bad Request");
}

TEST_F(ServerClientTest, InvalidLogicAfterValidInit) {
    sendCommandToServer("POST http://good.com\n");
    std::string response = sendCommandToServer("BOOM!!\n");
    EXPECT_EQ(response, "400 Bad Request");
}


// ✅ New test: client reconnects and still sees same state
TEST_F(ServerClientTest, ClientDisconnectReconnectSeesSameBloomState) {
    std::string postResponse = sendCommandToServer("POST http://reconnect.com\n");
    EXPECT_EQ(postResponse, "201 Created");

    // simulate disconnect + reconnect: open new connection
    std::string getResponse = sendCommandToServer("GET http://reconnect.com\n");
    EXPECT_EQ(getResponse, "200 Ok\n\ntrue true");
}

// ===============================
// Persistent Test
// ===============================

class PersistentServerTest : public ::testing::Test {
protected:
    void SetUp() override {
        cleanPersistenceFiles();
        sendCommandToServer("POST http://url1.com\n");
        sendCommandToServer("POST http://url2.com\n");
        sendCommandToServer("POST http://url3.com\n");
        sendCommandToServer("DELETE http://url3.com\n");
    }
};

TEST_F(PersistentServerTest, SimulatedPersistenceAcrossSessions) {
    std::string res1 = sendCommandToServer("GET http://url2.com\n");
    EXPECT_EQ(res1, "200 Ok\n\ntrue true");

    std::string res2 = sendCommandToServer("GET http://url3.com\n");
    EXPECT_EQ(res2, "200 Ok\n\ntrue false");
}

// ===============================
// Entry Point
// ===============================
int main(int argc, char **argv) {
    std::filesystem::create_directory("data");

    startServer();
    ::testing::InitGoogleTest(&argc, argv);
    int result = RUN_ALL_TESTS();
    stopServer();

    return result;
}