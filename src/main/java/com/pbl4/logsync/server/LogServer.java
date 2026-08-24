package com.pbl4.logsync.server;

import com.pbl4.logsync.protocol.Packet;
import java.io.*;
import java.net.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class LogServer {
    public static final int TCP_PORT = 9000;
    public static final int HTTP_PORT = 8080;

    public static final ConcurrentHashMap<String, Long> checkpoints = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, NodeStatus> nodeStatuses = new ConcurrentHashMap<>();
    
    // Lưu tối đa 200 logs gần nhất để web hiển thị mượt mà
    public static final List<LogEntry> receivedLogs = Collections.synchronizedList(new ArrayList<>());
    public static final List<ConnectionEvent> connectionHistory = Collections.synchronizedList(new ArrayList<>());

    // Cập nhật format ngày giờ chi tiết để web có thể filter Start - End
    public static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public static class NodeStatus {
        public String status = "ONLINE";
        public String lastSeen = TIME_FMT.format(Instant.now());
        public long lastSeq = 0;
    }

    public static class LogEntry {
        public String clientId;
        public long sequence;
        public byte level;
        public String message;
        public String timestamp;

        public LogEntry(String clientId, long sequence, byte level, String message) {
            this.clientId = clientId;
            this.sequence = sequence;
            this.level = level;
            this.message = message;
            this.timestamp = TIME_FMT.format(Instant.now());
        }
    }

    public static class ConnectionEvent {
        public String clientId;
        public String eventType;
        public String timestamp;
        
        public ConnectionEvent(String clientId, String eventType) {
            this.clientId = clientId;
            this.eventType = eventType;
            this.timestamp = TIME_FMT.format(Instant.now());
        }
    }

    public static void main(String[] args) throws IOException {
        WebDashboardServer.start(HTTP_PORT);
        ServerSocket serverSocket = new ServerSocket(TCP_PORT);
        System.out.println("[SERVER] Dashboard http://localhost:8080 | TCP Port 9000");

        while (true) {
            Socket socket = serverSocket.accept();
            new Thread(() -> handleClient(socket)).start();
        }
    }

    private static void handleClient(Socket socket) {
        String currentClient = "UNKNOWN";
        try (
            DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))
        ) {
            while (true) {
                Packet packet = Packet.readFrom(dis);
                
                if (currentClient.equals("UNKNOWN") && !packet.clientId.equals("UNKNOWN")) {
                    connectionHistory.add(new ConnectionEvent(packet.clientId, "CONNECTED"));
                }
                currentClient = packet.clientId;

                NodeStatus st = nodeStatuses.computeIfAbsent(currentClient, k -> new NodeStatus());
                st.status = "ONLINE";
                st.lastSeen = TIME_FMT.format(Instant.now());

                if (packet.type == Packet.TYPE_SYNC_REQ) {
                    long lastSeq = checkpoints.getOrDefault(packet.clientId, 0L);
                    new Packet(Packet.TYPE_SYNC_RESP, Packet.LEVEL_INFO, lastSeq, "SERVER", "SYNC_ACK").writeTo(dos);

                } else if (packet.type == Packet.TYPE_LOG) {
                    long lastSeq = checkpoints.getOrDefault(packet.clientId, 0L);
                    if (packet.sequence > lastSeq) {
                        checkpoints.put(packet.clientId, packet.sequence);
                        st.lastSeq = packet.sequence;
                        receivedLogs.add(new LogEntry(packet.clientId, packet.sequence, packet.level, packet.payload));
                        
                        // Giới hạn RAM: Chỉ giữ 200 log mới nhất
                        if (receivedLogs.size() > 200) receivedLogs.remove(0);
                    }
                    new Packet(Packet.TYPE_ACK, Packet.LEVEL_INFO, packet.sequence, packet.clientId, "ACK").writeTo(dos);
                }
            }
        } catch (Exception e) {
            if (!"UNKNOWN".equals(currentClient) && nodeStatuses.containsKey(currentClient)) {
                nodeStatuses.get(currentClient).status = "OFFLINE";
                connectionHistory.add(new ConnectionEvent(currentClient, "DISCONNECTED"));
            }
        }
    }
}