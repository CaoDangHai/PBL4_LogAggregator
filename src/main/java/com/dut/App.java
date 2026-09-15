package com.dut;

import com.dut.network.BlockingTCPConnection;
import com.dut.network.INetworkConnection;
import com.dut.server.ClientSession;
import com.dut.storage.LogStorage;
import com.dut.storage.PostgresLogStorage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App {
    public static void main(String[] args) {
        int port = 9000;

        // Đọc thông tin kết nối từ biến môi trường (Docker / file .env) hoặc dùng giá trị mặc định pbl4_db
        String host = System.getenv().getOrDefault("DB_HOST", "localhost");
        int dbPort = Integer.parseInt(System.getenv().getOrDefault("DB_PORT", "5432"));
        String dbName = System.getenv().getOrDefault("DB_NAME", "pbl4_db");
        String dbUser = System.getenv().getOrDefault("DB_USER", "postgres");
        String dbPassword = System.getenv().getOrDefault("DB_PASSWORD", "secretpassword");

        // Khởi tạo Storage kết nối thẳng tới pbl4_db
        LogStorage storage = new PostgresLogStorage(host, dbPort, dbName, dbUser, dbPassword);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            ExecutorService pool = Executors.newCachedThreadPool();
            System.out.println("[Server] Đang lắng nghe tại cổng " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                INetworkConnection conn = new BlockingTCPConnection(socket);
                pool.submit(new ClientSession(conn, storage));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}