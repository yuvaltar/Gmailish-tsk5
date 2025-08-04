# Compiler and flags
CXX = g++
CXXFLAGS = -std=c++17 -Wall
LDFLAGS = -pthread

# Directories
SRC_DIR = src

# Includes
INCLUDES = -I$(SRC_DIR)/BloomFilter -I$(SRC_DIR)/server

# Source files
MAIN_SRC = \
    $(SRC_DIR)/main.cpp \
    $(SRC_DIR)/BloomFilter/BloomFilter.cpp \
    $(SRC_DIR)/BloomFilter/BlackList.cpp \
    $(SRC_DIR)/BloomFilter/url.cpp \
    $(SRC_DIR)/server/server.cpp \
    $(SRC_DIR)/server/SessionHandler.cpp \
    $(SRC_DIR)/server/CommandManager.cpp

# Target
MAIN_TARGET = server

# Default target
all: $(MAIN_TARGET)

# Build main server binary
$(MAIN_TARGET): $(MAIN_SRC)
	$(CXX) $(CXXFLAGS) $(INCLUDES) -o $@ $^ $(LDFLAGS)

# Run server manually (optional)
run: $(MAIN_TARGET)
	./$(MAIN_TARGET)

# Clean all artifacts
clean:
	rm -rf *.o $(MAIN_TARGET)

.PHONY: all clean run
