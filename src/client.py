import socket  # Import the socket module to work with network connections

# Read server IP and port from user input (no prompt shown)
SERVER_IP = input()
SERVER_PORT = int(input())

# Create a TCP/IP socket
client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

try:
    # Try to connect to the server using the given IP and port
    client_socket.connect((SERVER_IP, SERVER_PORT))
    
    # Keep sending and receiving messages until the user types "quit"
    while True:
        # Get user input for the message to send
        message = input()
        
        # If user types "quit" (case-insensitive), break the loop and disconnect
        if message.strip().lower() == "quit":
            break

        # Send the message to the server with a newline character
        # The newline can help the server know where the message ends
        client_socket.send((message + '\n').encode('utf-8'))

        # Wait and receive the server's response (up to 4096 bytes)
        response = client_socket.recv(4096).decode('utf-8')
        
        # Print the server's response
        print(response.strip())

# If the user interrupts the program (Ctrl+C), ignore the error and exit gracefully
except KeyboardInterrupt:
    pass

# Catch any other exceptions (like connection errors) and ignore them
except Exception as e:
    pass

# Always close the socket when done, whether an error occurred or not
finally:
    client_socket.close()
