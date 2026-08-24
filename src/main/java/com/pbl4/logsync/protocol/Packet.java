package com.pbl4.logsync.protocol;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class Packet {
    public static final short MAGIC = (short) 0xCAFE;
    
    // Message Types
    public static final byte TYPE_SYNC_REQ = 1;
    public static final byte TYPE_SYNC_RESP = 2;
    public static final byte TYPE_LOG = 3;
    public static final byte TYPE_ACK = 4;

    // Log Levels
    public static final byte LEVEL_DEBUG = 1;
    public static final byte LEVEL_INFO = 2;
    public static final byte LEVEL_WARN = 3;
    public static final byte LEVEL_ERROR = 4;

    public byte type;
    public byte level; // Thêm Log Level
    public long sequence;
    public String clientId;
    public String payload;

    public Packet(byte type, byte level, long sequence, String clientId, String payload) {
        this.type = type;
        this.level = level;
        this.sequence = sequence;
        this.clientId = clientId;
        this.payload = payload != null ? payload : "";
    }

    public void writeTo(DataOutputStream dos) throws IOException {
        byte[] clientBytes = clientId.getBytes(StandardCharsets.UTF_8);
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

        dos.writeShort(MAGIC);
        dos.writeByte(type);
        dos.writeByte(level); // Ghi Level vào TCP Stream
        dos.writeLong(sequence);
        dos.writeInt(clientBytes.length);
        dos.write(clientBytes);
        dos.writeInt(payloadBytes.length);
        dos.write(payloadBytes);
        dos.flush();
    }

    public static Packet readFrom(DataInputStream dis) throws IOException {
        short magic = dis.readShort();
        if (magic != MAGIC) throw new IOException("Loi Magic Byte: " + Integer.toHexString(magic));
        
        byte type = dis.readByte();
        byte level = dis.readByte(); // Đọc Level từ TCP Stream
        long sequence = dis.readLong();

        int clientLen = dis.readInt();
        byte[] clientBytes = new byte[clientLen];
        dis.readFully(clientBytes);
        String clientId = new String(clientBytes, StandardCharsets.UTF_8);

        int payloadLen = dis.readInt();
        byte[] payloadBytes = new byte[payloadLen];
        dis.readFully(payloadBytes);
        String payload = new String(payloadBytes, StandardCharsets.UTF_8);

        return new Packet(type, level, sequence, clientId, payload);
    }
}