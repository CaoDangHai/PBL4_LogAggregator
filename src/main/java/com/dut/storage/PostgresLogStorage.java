package com.dut.storage;

import com.dut.protocol.LogPacket;
import java.sql.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class PostgresLogStorage implements LogStorage {
    private final String jdbcUrl;
    private final String dbUser;
    private final String dbPassword;


    // FIX: Tự xây dựng Connection Pool thuần Java để chịu tải cao
    //NOTE: NÊN DÙNG HikariCP , 
    private static final int POOL_SIZE = 10;
    private final BlockingQueue<Connection> connectionPool;

    public PostgresLogStorage(String host, int port, String dbName, String user, String password) {
        this.jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + dbName;
        this.dbUser = user;
        this.dbPassword = password;
        
        this.connectionPool = new ArrayBlockingQueue<>(POOL_SIZE);
        initConnectionPool();
        initDatabaseTables();
    }

    private void initConnectionPool() {
        try {
            for (int i = 0; i < POOL_SIZE; i++) {
                connectionPool.offer(createNewConnection());
            }
            System.out.println("[Database] Đã khởi tạo Connection Pool với " + POOL_SIZE + " kết nối.");
        } catch (SQLException e) {
            System.err.println("[Database] Lỗi khởi tạo Pool: " + e.getMessage());
        }
    }

    private Connection createNewConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
    }

    // Mượn kết nối từ hồ chứa
    private Connection getConnection() throws InterruptedException, SQLException {
        Connection conn = connectionPool.take();
        if (conn.isClosed()) {
            conn = createNewConnection();
        }
        return conn;
    }

    // Trả kết nối lại hồ chứa sau khi dùng xong
    private void releaseConnection(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.isClosed()) {
                    connectionPool.offer(conn);
                } else {
                    connectionPool.offer(createNewConnection());
                }
            } catch (SQLException e) {
                try {
                    connectionPool.offer(createNewConnection());
                } catch (SQLException ignored) {}
            }
        }
    }

    private void initDatabaseTables() {
        String createLogsTable = "CREATE TABLE IF NOT EXISTS system_logs (" +
                "id SERIAL PRIMARY KEY, application_id VARCHAR(100), client_id VARCHAR(100), " +
                "message_id VARCHAR(150) UNIQUE, sequence_number BIGINT, timestamp BIGINT, " +
                "log_level INT, payload TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        
        String createCheckpointsTable = "CREATE TABLE IF NOT EXISTS client_checkpoints (" +
                "client_id VARCHAR(100) PRIMARY KEY, last_sequence BIGINT)";

        Connection conn = null;
        try {
            conn = getConnection();
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createLogsTable);
                stmt.execute(createCheckpointsTable);
            }
        } catch (Exception e) {
            System.err.println("[Database] Lỗi khởi tạo bảng: " + e.getMessage());
        } finally {
            releaseConnection(conn);
        }
    }

    @Override
    public SaveStatus saveLog(LogPacket packet) {
        String sql = "INSERT INTO system_logs (application_id, client_id, message_id, sequence_number, timestamp, log_level, payload) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (message_id) DO NOTHING";
                     
        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setString(1, packet.getApplicationId());
            pstmt.setString(2, packet.getClientId());
            pstmt.setString(3, packet.getMessageId());
            pstmt.setLong(4, packet.getSequenceNumber());
            pstmt.setLong(5, packet.getTimestamp());
            pstmt.setInt(6, packet.getLogLevel());
            pstmt.setString(7, packet.getPayload());
            
            int affectedRows = pstmt.executeUpdate();
            
            if (affectedRows > 0) {
                updateClientCheckpoint(packet.getClientId(), packet.getSequenceNumber());
                return SaveStatus.SUCCESS;
            }
            // Nếu insert bị DB bỏ qua do trùng message_id
            return SaveStatus.DUPLICATE; 
            
        } catch (SQLException e) {
            System.err.println("[Database] Lỗi khi lưu log vào Postgres: " + e.getMessage());
            // Trả về ERROR rõ ràng khi rớt kết nối DB
            return SaveStatus.ERROR; 
        }
    }

    // Hàm nội bộ dùng chung Connection để tối ưu hiệu suất
    private void updateClientCheckpointInternal(Connection conn, String clientId, long lastSequence) {
        String sql = "INSERT INTO client_checkpoints (client_id, last_sequence) VALUES (?, ?) " +
                     "ON CONFLICT (client_id) DO UPDATE SET last_sequence = EXCLUDED.last_sequence";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, clientId);
            pstmt.setLong(2, lastSequence);
            pstmt.executeUpdate();
        } catch (SQLException ignored) {}
    }

    @Override
    public void updateClientCheckpoint(String clientId, long lastSequence) {
        Connection conn = null;
        try {
            conn = getConnection();
            updateClientCheckpointInternal(conn, clientId, lastSequence);
        } catch (Exception ignored) {
        } finally {
            releaseConnection(conn);
        }
    }

    @Override
    public long getClientCheckpoint(String clientId) {
        String sql = "SELECT last_sequence FROM client_checkpoints WHERE client_id = ?";
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, clientId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return rs.getLong("last_sequence");
                }
            }
        } catch (Exception ignored) {
        } finally {
            releaseConnection(conn);
        }
        return 0;
    }
}