FROM node:18-alpine

# Set working directory
WORKDIR /usr/src/app

# Copy dependency files first for better Docker cache
COPY package*.json ./

# Install dependencies
RUN npm install

# Copy the rest of your application code
COPY . .

# Expose the port your app runs on
EXPOSE 3000

# Start the server
CMD ["node", "app.js"]
