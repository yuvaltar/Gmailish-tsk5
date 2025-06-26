FROM node:18-alpine

WORKDIR /app

# Copy the docker-specific package.json instead of the default
COPY package.docker.json package.json
COPY package-lock.json .

RUN npm install

COPY . .

EXPOSE 3001

CMD ["npm", "start"]
