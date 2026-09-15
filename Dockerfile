# Stage 1: Build file jar bằng Maven
FROM maven:3.8.5-openjdk-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Chạy ứng dụng với JRE gọn nhẹ
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/pbl4-log-server-1.0-SNAPSHOT.jar app.jar