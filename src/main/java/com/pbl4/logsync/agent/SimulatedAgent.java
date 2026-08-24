package com.pbl4.logsync.agent;

import com.pbl4.logsync.protocol.Packet;
import java.io.*;
import java.net.Socket;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class SimulatedAgent {
    private static final String SERVER_HOST = System.getenv().getOrDefault("SERVER_HOST", "127.0.0.1");
    private static final int SERVER_PORT = 9000;
    private static final String NODE_ID = System.getenv().getOrDefault("NODE_ID", "NODE_01");

    private static final Map<Long, Packet> logStorage = new ConcurrentHashMap<>();
    private static long sequenceCounter = 0;
    private static final Random random = new Random();

    // Dữ liệu mô phỏng backend thực tế
    private static final String[] infoLogs = {
        "NestJS [AppModule] Dependencies initialized successfully",
        "FastAPI [Worker] Listening at: http://0.0.0.0:8000",
        "ASP.NET Core Entity Framework migration applied",
        "PostgreSQL connection pool established",
        "HTTP GET /api/v1/users - 200 OK - 45ms"
    };
    private static final String[] warnLogs = {
        "High memory usage detected (85%) in JVM",
        "Slow query in PostgreSQL table 'orders' (450ms)",
        "Rate limiting triggered for IP 192.168.1.100"
    };
    private static final String[] errorLogs = {
        "Redis connection refused: Connection timed out",
        "Unhandled exception in AuthenticationGuard",
        "Deadlock detected during transaction serialization"
    };

    public static void main(String[] args) {
        System.out.printf("[NODE %s] Khoi dong...%n", NODE_ID);
        new Thread(SimulatedAgent::simulateLogGenerator).start();
        networkSyncLoop();
    }

    private static void simulateLogGenerator() {
        while (true) {
            try {
                Thread.sleep(random.nextInt(3000) + 1500); // Random 1.5s - 4.5s
                synchronized (logStorage) {
                    sequenceCounter++;
                    int r = random.nextInt(100);
                    byte level;
                    String message;

                    if (r < 60) {
                        level = Packet.LEVEL_INFO;
                        message = infoLogs[random.nextInt(infoLogs.length)];
                    } else if (r < 85) {
                        level = Packet.LEVEL_WARN;
                        message = warnLogs[random.nextInt(warnLogs.length)];
                    } else {
                        level = Packet.LEVEL_ERROR;
                        message = errorLogs[random.nextInt(errorLogs.length)];
                    }

                    Packet pkt = new Packet(Packet.TYPE_LOG, level, sequenceCounter, NODE_ID, message);
                    logStorage.put(sequenceCounter, pkt);
                }
            } catch (InterruptedException e) { break; }
        }
    }

    private static void networkSyncLoop() {
        while (true) {
            try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                 DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                 DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {

                new Packet(Packet.TYPE_SYNC_REQ, Packet.LEVEL_INFO, 0, NODE_ID, "").writeTo(dos);
                Packet syncResp = Packet.readFrom(dis);
                long lastServerSeq = syncResp.sequence;
                
                logStorage.keySet().removeIf(seq -> seq <= lastServerSeq);

                while (true) {
                    List<Long> pendingSeqs;
                    synchronized (logStorage) { pendingSeqs = new ArrayList<>(logStorage.keySet()); }
                    Collections.sort(pendingSeqs);

                    for (Long seq : pendingSeqs) {
                        Packet pkt = logStorage.get(seq);
                        if (pkt != null) {
                            pkt.writeTo(dos);
                            socket.setSoTimeout(3000);
                            Packet ack = Packet.readFrom(dis);
                            if (ack.type == Packet.TYPE_ACK && ack.sequence == seq) {
                                logStorage.remove(seq);
                            }
                        }
                    }
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            }
        }
    }
}