# Stage 1: Build frontend
FROM node:20-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN npm install

COPY . .
RUN npm run build

# Stage 2: Serve static files
FROM node:20-alpine
WORKDIR /app

# Copy build output từ stage builder
COPY --from=builder /app/build ./build

# Cài serve để chạy static files
RUN npm install -g serve

EXPOSE 3000
CMD ["serve", "-s", "build", "-l", "3000"]
