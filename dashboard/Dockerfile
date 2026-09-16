# Stage 1: Build React/Vite UI
FROM node:18-alpine AS ui-builder
WORKDIR /app/ui
COPY ui/package*.json ./
RUN npm install
COPY ui/ ./
RUN npm run build

# Stage 2: Chạy Node.js Express Server
FROM node:18-alpine
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
# Copy file React đã build đè vào thư mục public của Express
COPY --from=ui-builder /app/ui/dist ./public 
EXPOSE 3000
CMD ["node", "server.js"]