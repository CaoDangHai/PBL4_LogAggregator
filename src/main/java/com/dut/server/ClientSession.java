package com.dut.server;

import com.dut.network.INetworkConnection;
import com.dut.protocol.LogPacket;
import com.dut.protocol.MessageType;
import com.dut.protocol.ProtocolCodec;
import com.dut.storage.LogStorage;

import java.io.IOException;

/**
 * Xử lý luồng đọc/ghi độc lập cho từng Log Agent kết nối đến Server.
 */
public class ClientSession implements Runnable {
    private final INetworkConnection connection;
    private final LogStorage storage; // Khai báo kho chứa lưu database
    private final String remoteAddress;
    private volatile boolean running = true;
    private String clientId = "UNKNOWN";

    // Cập nhật constructor nhận thêm LogStorage từ App.java
    public ClientSession(INetworkConnection connection, LogStorage storage) {
        this.connection = connection;
        this.storage = storage;
        this.remoteAddress = connection.getRemoteAddress();
    }

    @Override
    public void run() {
        System.out.println("[Server] Đã chấp nhận kết nối từ Agent: " + remoteAddress);

        try {
            while (running && connection.isConnected()) {
                // 1. Nhận luồng byte thô từ tầng mạng (đã qua TCP Framing)
                byte[] rawData = connection.receive();
                if (rawData == null) {
                    // Nhận tín hiệu EOF (Agent ngắt kết nối chủ động)
                    break;
                }

                // KIỂM TRA AN TOÀN: Tránh gói tin quá ngắn gây lỗi BufferUnderflow
                if (rawData.length < 4) {
                    System.err.println("[Server] Cảnh báo: Nhận gói tin quá ngắn (" + rawData.length + " bytes), bỏ qua.");
                    continue;
                }

                try {
                    // 2. Giải mã byte thành LogPacket bằng ProtocolCodec
                    LogPacket packet = ProtocolCodec.deserialize(rawData);
                    this.clientId = packet.getClientId();

                    // 3. Phân loại xử lý dựa trên MessageType
                    handlePacket(packet);
                } catch (Exception parseEx) {
                    // Bắt lỗi giải mã riêng cho từng gói tin để không làm chết cả phiên kết nối socket
                    System.err.println("[Server] Lỗi giải mã gói tin từ " + remoteAddress + ": " + parseEx.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[Server] Lỗi phiên làm việc với Agent [" + clientId + "] tại " + remoteAddress + ": " + e.getMessage());
        } finally {
            closeSession();
        }
    }

    private void handlePacket(LogPacket packet) throws IOException {
        switch (packet.getMessageType()) {
            case MessageType.CONNECT:
                System.out.println("[Server] Nhận yêu cầu CONNECT từ Client: " + packet.getClientId());
                break;

            case MessageType.LOG:
                System.out.println("[Client Log][" + packet.getClientId() + "] Seq: " + packet.getSequenceNumber() 
                         + " | Level: " + packet.getLogLevel() + " | Msg: " + packet.getPayload());
                
                if (storage != null) {
                    LogStorage.SaveStatus status = storage.saveLog(packet);
                    
                    if (status == LogStorage.SaveStatus.SUCCESS) {
                        System.out.println("[Database] Lưu thành công log mới vào pbl4_db.");
                        sendAck(packet.getClientId(), packet.getSequenceNumber());
                        
                    } else if (status == LogStorage.SaveStatus.DUPLICATE) {
                        System.out.println("[Database] Phát hiện log trùng lặp (Deduplicated). Gửi lại ACK để Client yên tâm.");
                        sendAck(packet.getClientId(), packet.getSequenceNumber());
                        
                    } else if (status == LogStorage.SaveStatus.ERROR) {
                        // KHÔNG GỬI ACK. 
                        // Client sẽ không nhận được ACK -> Hết Timeout Client sẽ tự động Retry.
                        System.err.println("[Server] Lưu DB thất bại. TỪ CHỐI gửi ACK cho Seq: " + packet.getSequenceNumber());
                    }
                }
                break;  

            case MessageType.HEARTBEAT:
                System.out.println("[Server] Nhận Heartbeat từ Client: " + packet.getClientId());
                break;

            case MessageType.DISCONNECT:
                System.out.println("[Server] Client yêu cầu DISCONNECT: " + packet.getClientId());
                running = false;
                break;

            default:
                System.out.println("[Server] Nhận loại MessageType không xác định: " + packet.getMessageType());
                break;
        }
    }

    private void sendAck(String targetClientId, long sequenceNumber) {
        try {
            // Tạo gói tin ACK đơn giản phản hồi về cho Agent
            LogPacket ackPacket = new LogPacket(
                    MessageType.ACK,
                    "SYSTEM",
                    targetClientId,
                    "ACK-" + sequenceNumber,
                    sequenceNumber,
                    System.currentTimeMillis(),
                    1,
                    "OK"
            );
            byte[] ackData = ProtocolCodec.serialize(ackPacket);
            connection.send(ackData);
        } catch (IOException e) {
            System.err.println("[Server] Không thể gửi ACK tới Client: " + e.getMessage());
        }
    }

    private void closeSession() {
        try {
            connection.close();
            System.out.println("[Server] Đã đóng và giải phóng phiên kết nối của Agent: " + remoteAddress);
        } catch (Exception e) {
            System.err.println("[Server] Lỗi khi đóng session: " + e.getMessage());
        }
    }
}