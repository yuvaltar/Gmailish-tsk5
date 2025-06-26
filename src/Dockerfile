FROM ubuntu:22.04

RUN apt-get update && apt-get install -y \
    build-essential \
    cmake \
    git \
    python3 \
    libgtest-dev \
 && apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY . .

RUN g++ main.cpp BloomFilter/BloomFilter.cpp BloomFilter/BlackList.cpp BloomFilter/url.cpp server/server.cpp server/SessionHandler.cpp server/CommandManager.cpp -I. -IBloomFilter -Iserver -o cpp_server -pthread -std=gnu++17

EXPOSE 4000

CMD ["./cpp_server", "4000", "1024", "3", "5"]