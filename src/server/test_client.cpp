#include <iostream>
#include <thread>
#include <vector>
#include <random>
#include <cstring>
#include <chrono>
#include <mutex>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>

// Server config
const char* SERVER_IP = "127.0.0.1";
const int SERVER_PORT = 4000;

// Shared mutex to safely print to console from multiple threads
std::mutex coutMutex;

std::string getRandomCommand() {
    static const std::vector<std::string> verbs = {"POST", "GET", "DELETE"};
    static const std::vector<std::string> urls = {
        "http://example.com", "http://google.com", "http://test.com/page",
        "http://my.site/path", "http://127.0.0.1/home", "http://localhost:8080"
    };

    static thread_local std::mt19937 rng(std::random_device{}());
    std::uniform_int_distribution<int> verbDist(0, verbs.size() - 1);
    std::uniform_int_distribution<int> urlDist(0, urls.size() - 1);

    return verbs[verbDist(rng)] + " " + urls[urlDist(rng)];
}

void sendCommand(int threadId) {
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) {
        std::lock_guard<std::mutex> lock(coutMutex);
        std::cerr << "[Thread " << threadId << "] Failed to create socket\n";
        return;
    }

    sockaddr_in serverAddr{};
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_port = htons(SERVER_PORT);
    inet_pton(AF_INET, SERVER_IP, &serverAddr.sin_addr);

    if (connect(sock, (sockaddr*)&serverAddr, sizeof(serverAddr)) < 0) {
        std::lock_guard<std::mutex> lock(coutMutex);
        std::cerr << "[Thread " << threadId << "] Failed to connect\n";
        close(sock);
        return;
    }

    std::string command = getRandomCommand();
    std::string message = command + "\n";

    send(sock, message.c_str(), message.size(), 0);

    char buffer[1024] = {0};
    ssize_t bytesReceived = recv(sock, buffer, sizeof(buffer) - 1, 0);

    {
        std::lock_guard<std::mutex> lock(coutMutex);
        std::cout << "[Thread " << threadId << "] Sent: " << command
                  << " | Received: " << std::string(buffer, bytesReceived) << "\n";
    }

    close(sock);
}

int main() {
    const int threadCount = 50;
    std::vector<std::thread> threads;

    for (int i = 0; i < threadCount; ++i) {
        threads.emplace_back(sendCommand, i);
        std::this_thread::sleep_for(std::chrono::milliseconds(10));  // slight stagger
    }

    for (auto& t : threads) t.join();

    std::cout << "All threads completed.\n";
    return 0;
}
