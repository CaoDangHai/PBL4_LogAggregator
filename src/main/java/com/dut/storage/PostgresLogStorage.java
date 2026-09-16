package com.dut.storage;

import com.dut.protocol.LogPacket;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;

public class PostgresLogStorage implements LogStorage {
    private final HikariDataSource dataSource;

    public PostgresLogStorage(String host, int port, String dbName, String user, String password) {
        String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + dbName;

        // Cấu hình HikariCP tối ưu
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(20); // Giới hạn max 20 kết nối đồng thời
        config.setMinimumIdle(5); // Luôn giữ 5 kết nối dự phòng
        config.setConnectionTimeout(10000); // Timeout lấy kết nối: 10s
        config.setIdleTimeout(600000); // Giải phóng kết nối rỗi sau 10 phút

        this.dataSource = new HikariDataSource(config);
        initDatabaseTables();
    }

    private void initDatabaseTables() {
        String createLogsTable = "CREATE TABLE IF NOT EXISTS system_logs (" +
                "id SERIAL PRIMARY KEY, application_id VARCHAR(100), client_id VARCHAR(100), " +
                "message_id VARCHAR(150) UNIQUE, sequence_number BIGINT, timestamp BIGINT, " +
                "log_level INT, payload TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";

        String createCheckpointsTable = "CREATE TABLE IF NOT EXISTS client_checkpoints (" +
                "client_id VARCHAR(100) PRIMARY KEY, last_sequence BIGINT)";

        // Try-with-resources tự động trả connection về Pool sau khi dùng xong
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(createLogsTable);
            stmt.execute(createCheckpointsTable);
            System.out.println("[Database] Khởi tạo DB và cấu hình HikariCP thành công.");
        } catch (Exception e) {
            System.err.println("[Database] Lỗi khởi tạo bảng: " + e.getMessage());
        }
    }

    @Override
    public SaveStatus saveLog(LogPacket packet) {
        String sql = "INSERT INTO system_logs (application_id, client_id, message_id, sequence_number, timestamp, log_level, payload) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (message_id) DO NOTHING";

        try (Connection conn = dataSource.getConnection();
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
                // Tái sử dụng chính connection hiện tại để lưu checkpoint cho nhanh
                updateClientCheckpointInternal(conn, packet.getClientId(), packet.getSequenceNumber());
                return SaveStatus.SUCCESS;
            }
            return SaveStatus.DUPLICATE;

        } catch (SQLException e) {
            System.err.println("[Database] Lỗi khi lưu log vào Postgres: " + e.getMessage());
            return SaveStatus.ERROR;
        }
    }

    private void updateClientCheckpointInternal(Connection conn, String clientId, long lastSequence) {
        String sql = "INSERT INTO client_checkpoints (client_id, last_sequence) VALUES (?, ?) " +
                "ON CONFLICT (client_id) DO UPDATE SET last_sequence = EXCLUDED.last_sequence";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, clientId);
            pstmt.setLong(2, lastSequence);
            pstmt.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    @Override
    public void updateClientCheckpoint(String clientId, long lastSequence) {
        try (Connection conn = dataSource.getConnection()) {
            updateClientCheckpointInternal(conn, clientId, lastSequence);
        } catch (Exception ignored) {
        }
    }

    @Override
    public long getClientCheckpoint(String clientId) {
        String sql = "SELECT last_sequence FROM client_checkpoints WHERE client_id = ?";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, clientId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next())
                    return rs.getLong("last_sequence");
            }
        } catch (Exception ignored) {
        }
        return 0;
    }
}