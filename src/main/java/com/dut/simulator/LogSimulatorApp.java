package com.dut.simulator;

import com.dut.client.LogAgent;
import java.util.Random;

public class LogSimulatorApp {
    private static final Random random = new Random();

    public static void main(String[] args) {
        String serverHost = System.getenv().getOrDefault("SERVER_HOST", "localhost");
        int serverPort = 9000;

        // 10 Microservices cấu thành nên MỘT hệ thống Production duy nhất (Nebula-Shop)
        String[][] services = {
            {"Nebula-Shop", "API-Gateway-NodeJS"},           // Điều hướng request đầu vào
            {"Nebula-Shop", "Auth-Service-Go"},              // Xác thực & Phân quyền (JWT)
            {"Nebula-Shop", "User-Service-NestJS"},          // Quản lý hồ sơ người dùng
            {"Nebula-Shop", "Product-Catalog-SpringBoot"},   // Quản lý sản phẩm & danh mục
            {"Nebula-Shop", "Inventory-Service-SpringBoot"}, // Quản lý kho hàng tồn kho
            {"Nebula-Shop", "Cart-Service-Redis"},           // Giỏ hàng tạm thời
            {"Nebula-Shop", "Order-Service-AspNetCore"},     // Xử lý đơn hàng cốt lõi
            {"Nebula-Shop", "Payment-Gateway-FastAPI"},      // Tích hợp thanh toán (Visa/VNPay)
            {"Nebula-Shop", "Shipping-Service-AspNetCore"},  // Vận chuyển & Logistics
            {"Nebula-Shop", "Recommendation-Engine-Python"}  // Gợi ý sản phẩm bằng AI
        };

        System.out.println("[Simulator] Đang khởi động hệ sinh thái Nebula-Shop với 10 Microservices...");

        // Khởi chạy 10 luồng độc lập đồng thời, cùng xả log về Server
        for (String[] svc : services) {
            startSimulatedApp(serverHost, serverPort, svc[0], svc[1]);
        }
    }

    private static void startSimulatedApp(String host, int port, String appId, String clientId) {
        Thread appThread = new Thread(() -> {
            LogAgent agent = new LogAgent(host, port, appId, clientId);
            agent.start();

            while (true) {
                try {
                    // Phân bổ tỷ lệ log: 85% INFO, 12% WARN, 3% ERROR (chuẩn hệ thống ổn định)
                    int chance = random.nextInt(100);
                    int level = (chance < 85) ? 1 : (chance < 97 ? 2 : 3);
                    
                    String message = generateRealisticLog(clientId, level);
                    agent.log(level, message);

                    // Tốc độ xả log: từ 30ms đến 150ms mỗi dòng (Mô phỏng traffic cao)
                    Thread.sleep(random.nextInt(120) + 30);
                } catch (InterruptedException e) {
                    agent.stop();
                    break;
                }
            }
        }, "Simulator-" + clientId);
        appThread.start();
    }

    private static String generateRealisticLog(String clientId, int level) {
        String traceId = java.util.UUID.randomUUID().toString().substring(0, 8);
        String ip = "10.0." + random.nextInt(5) + "." + (random.nextInt(200) + 10);

        if (clientId.contains("SpringBoot")) {
            return generateSpringBootLogs(level, traceId, clientId);
        } else if (clientId.contains("AspNetCore")) {
            return generateAspNetLogs(level, traceId, clientId);
        } else if (clientId.contains("Python") || clientId.contains("FastAPI")) {
            return generatePythonLogs(level, ip, clientId);
        } else {
            return generateNodeGoLogs(level, ip, clientId); // NodeJS, NestJS, Go
        }
    }

    // 1. Log chuẩn Java Spring Boot (Product, Inventory)
    private static String generateSpringBootLogs(int level, String traceId, String service) {
        String[] infoLogs = {
            "INFO [" + service + ", " + traceId + "] - [HikariPool-1] Pool is ready.",
            "INFO [" + service + ", " + traceId + "] - Fetching cache from Redis...",
            "INFO [" + service + ", " + traceId + "] - Hibernate: select p0_.id as id, p0_.price as price from products p0_ where p0_.status=1"
        };
        String[] warnLogs = {
            "WARN [" + service + ", " + traceId + "] - Execution time for query exceeded 500ms threshold.",
            "WARN [" + service + ", " + traceId + "] - Cache miss for key: item_details_8829"
        };
        String[] errorLogs = {
            "ERROR [" + service + ", " + traceId + "] - org.springframework.dao.CannotAcquireLockException: Transaction deadlock detected.",
            "ERROR [" + service + ", " + traceId + "] - java.net.ConnectException: Connection refused to RabbitMQ cluster."
        };

        if (level == 1) return infoLogs[random.nextInt(infoLogs.length)];
        if (level == 2) return warnLogs[random.nextInt(warnLogs.length)];
        return errorLogs[random.nextInt(errorLogs.length)];
    }

    // 2. Log chuẩn ASP.NET Core (Order, Shipping)
    private static String generateAspNetLogs(int level, String traceId, String service) {
        String[] infoLogs = {
            "INFO [" + traceId + "] Microsoft.AspNetCore.Hosting.Diagnostics: Request finished HTTP/1.1 POST /api/v1/orders 201 Created",
            "INFO [" + traceId + "] Microsoft.EntityFrameworkCore.Database.Command: Executed DbCommand (12ms) [Parameters=[@p0='?']]",
            "INFO [" + traceId + "] MassTransit: Message published to exchange: order-created-exchange"
        };
        String[] warnLogs = {
            "WARN [" + traceId + "] Microsoft.AspNetCore.HttpsPolicy.HttpsRedirectionMiddleware: Failed to determine the https port for redirect.",
            "WARN [" + traceId + "] " + service + ": External logistics API response delayed (> 2000ms)."
        };
        String[] errorLogs = {
            "FAIL [" + traceId + "] Microsoft.AspNetCore.Diagnostics.ExceptionHandlerMiddleware: Unhandled exception. Microsoft.EntityFrameworkCore.DbUpdateConcurrencyException",
            "CRIT [" + traceId + "] System.OutOfMemoryException: Exception of type 'System.OutOfMemoryException' was thrown."
        };

        if (level == 1) return infoLogs[random.nextInt(infoLogs.length)];
        if (level == 2) return warnLogs[random.nextInt(warnLogs.length)];
        return errorLogs[random.nextInt(errorLogs.length)];
    }

    // 3. Log chuẩn Python/FastAPI (Payment, AI)
    private static String generatePythonLogs(int level, String ip, String service) {
        String[] infoLogs = {
            "INFO:     [" + service + "] " + ip + " - \"POST /api/checkout/process HTTP/1.1\" 200 OK",
            "INFO:     [" + service + "] Celery worker received task: generate_recommendations",
            "INFO:     [" + service + "] Connection to Stripe API established successfully."
        };
        String[] warnLogs = {
            "WARNING:  [" + service + "] Payment gateway responded with HTTP 429 Too Many Requests. Applying exponential backoff.",
            "WARNING:  [" + service + "] GPU memory utilization at 92%, scaling up workers."
        };
        String[] errorLogs = {
            "ERROR:    [" + service + "] Exception in ASGI application: psycopg2.OperationalError: server closed the connection unexpectedly",
            "ERROR:    [" + service + "] StripeCardError: The card has been declined."
        };

        if (level == 1) return infoLogs[random.nextInt(infoLogs.length)];
        if (level == 2) return warnLogs[random.nextInt(warnLogs.length)];
        return errorLogs[random.nextInt(errorLogs.length)];
    }

    // 4. Log chuẩn Node.js/NestJS/Go (Gateway, Auth, User)
    private static String generateNodeGoLogs(int level, String ip, String service) {
        String[] infoLogs = {
            "[Nest] 42 - [" + service + "] Route matched: {/api/users/profile, GET}",
            "[Gateway] Request proxied to downstream service /api/products",
            "[Auth] JWT Token validated for User ID: " + (random.nextInt(9000) + 1000) + " from IP: " + ip
        };
        String[] warnLogs = {
            "[RateLimiter] IP " + ip + " is approaching rate limit (95/100 requests per minute).",
            "[Gateway] Upstream service Order-Service responded slowly (Timeout nearing)."
        };
        String[] errorLogs = {
            "[ExceptionsHandler] UnauthorizedException: JWT expired at " + System.currentTimeMillis(),
            "[Gateway] 502 Bad Gateway - Downstream service Auth-Service is unreachable."
        };

        if (level == 1) return infoLogs[random.nextInt(infoLogs.length)];
        if (level == 2) return warnLogs[random.nextInt(warnLogs.length)];
        return errorLogs[random.nextInt(errorLogs.length)];
    }
}