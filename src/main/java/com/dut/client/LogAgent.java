package com.dut.client;

import com.dut.storage.SimpleWAL;
import com.dut.network.BlockingTCPConnection;
import com.dut.network.INetworkConnection;
import com.dut.protocol.LogPacket;
import com.dut.protocol.MessageType;
import com.dut.protocol.ProtocolCodec;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class LogAgent {
    private final String serverHost;
    private final int serverPort;
    private final String applicationId;
    private final String clientId;
    private final SimpleWAL wal;
    private INetworkConnection connection;
    private volatile boolean running = false;

    private final BlockingQueue<LogPacket> sendQueue = new LinkedBlockingQueue<>(10000);
    private final ConcurrentHashMap<Long, LogQueueItem> unackedMap = new ConcurrentHashMap<>();

    private long sequenceCounter = 0;
    private Thread senderThread;
    private Thread receiverThread;

    public LogAgent(String serverHost, int serverPort, String applicationId, String clientId) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.applicationId = applicationId;
        this.clientId = clientId;
        this.wal = new SimpleWAL(clientId);
    }

    public synchronized void start() {
        if (running) return;
        running = true;

        List<LogPacket> recovered = wal.recoverLogs();
        for (LogPacket p : recovered) {
            try {
                sendQueue.put(p);
            } catch (InterruptedException ignored) {}
        }
        wal.clear(); 

        connectToServer();

        senderThread = new Thread(this::senderLoop, "Agent-Sender-Thread");
        senderThread.start();

        receiverThread = new Thread(this::receiverLoop, "Agent-Receiver-Thread");
        receiverThread.start();

        System.out.println("[Agent] Khởi động thành công cho Client ID: " + clientId);
    }

    private void connectToServer() {
        while (running) {
            try {
                Socket socket = new Socket(serverHost, serverPort);
                this.connection = new BlockingTCPConnection(socket);
                sendConnectPacket();
                break;
            } catch (IOException e) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {}
            }
        }
    }

    private void sendConnectPacket() {
        try {
            LogPacket connectPacket = new LogPacket(
                    MessageType.CONNECT, applicationId, clientId,
                    "CONNECT-" + System.currentTimeMillis(),
                    0, System.currentTimeMillis(), 1, "HELLO_SERVER"
            );
            connection.send(ProtocolCodec.serialize(connectPacket));
        } catch (IOException ignored) {}
    }

    public void log(int level, String message) {
        long seq = ++sequenceCounter;
        LogPacket packet = new LogPacket(
                MessageType.LOG, applicationId, clientId,
                clientId + "-" + System.currentTimeMillis() + "-" + seq,
                seq, System.currentTimeMillis(), level, message
        );
        
        wal.append(packet);
        
        // Thay vì sendQueue.put(packet);
    if (!sendQueue.offer(packet)) {
    System.err.println("[Agent] Queue đầy, drop log để cứu app!");
        }
    }

    private void senderLoop() {
        while (running) {
            try {
                LogPacket packet = sendQueue.poll(500, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    checkAndRetryUnacked();
                    continue;
                }
                unackedMap.put(packet.getSequenceNumber(), new LogQueueItem(packet));
                sendPacket(packet);
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                handleNetworkFailure();
            }
        }
    }

    private void sendPacket(LogPacket packet) throws IOException {
        if (connection == null || !connection.isConnected()) {
            throw new IOException("Mất mạng.");
        }
        byte[] data = ProtocolCodec.serialize(packet);
        connection.send(data);
    }

    private void receiverLoop() {
        while (running) {
            try {
                if (connection == null || !connection.isConnected()) {
                    Thread.sleep(1000);
                    continue;
                }
                byte[] rawData = connection.receive();
                if (rawData == null) {
                    handleNetworkFailure();
                    continue;
                }
                LogPacket response = ProtocolCodec.deserialize(rawData);
                if (response.getMessageType() == MessageType.ACK) {
                    long ackSeq = response.getSequenceNumber();
                    unackedMap.remove(ackSeq);
                    
                    // FIX: Quản lý ổ cứng (Log Rotation / Compaction)
                    if (unackedMap.isEmpty()) {
                        wal.clear(); // Nếu mạng thông suốt, gửi xong hết thì xóa WAL
                    } else if (unackedMap.size() > 0 && unackedMap.size() % 500 == 0) {
                        // Nếu đang kẹt nhiều log, định kỳ dọn rác file WAL
                        List<LogPacket> pending = new ArrayList<>();
                        for(LogQueueItem item : unackedMap.values()) {
                            pending.add(item.getPacket());
                        }
                        wal.compact(pending);
                    }
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                handleNetworkFailure();
            }
        }
    }

    private void checkAndRetryUnacked() {
        long now = System.currentTimeMillis();
        for (LogQueueItem item : unackedMap.values()) {
            if (now - item.getLastSentTimestamp() > 3000) {
                if (item.getretryCount() >= 5) {
                    unackedMap.remove(item.getPacket().getSequenceNumber());
                    continue;
                }
                item.incrementRetry();
                try {
                    sendPacket(item.getPacket());
                } catch (IOException e) {
                    handleNetworkFailure();
                    break;
                }
            }
        }
    }

    private synchronized void handleNetworkFailure() {
    if (connection != null && connection.isConnected()) return; // Thêm dòng này
    try {
        if (connection != null) connection.close();
    } catch (Exception ignored) {}
    connectToServer();
}

    public synchronized void stop() {
        running = false;
        if (senderThread != null) senderThread.interrupt();
        if (receiverThread != null) receiverThread.interrupt();
        try {
            if (connection != null) connection.close();
        } catch (Exception ignored) {}
    }
}