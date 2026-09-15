package com.dut.protocol;

public class LogPacket {
    private byte messageType;
    private String applicationId;
    private String clientId;
    private String messageId;
    private long sequenceNumber;
    private long timestamp;
    private int logLevel; // 1: INFO, 2: WARN, 3: ERROR
    private String payload;

    public LogPacket(byte messageType, String applicationId, String clientId, 
                     String messageId, long sequenceNumber, long timestamp, 
                     int logLevel, String payload) {
        this.messageType = messageType;
        this.applicationId = applicationId;
        this.clientId = clientId;
        this.messageId = messageId;
        this.sequenceNumber = sequenceNumber;
        this.timestamp = timestamp;
        this.logLevel = logLevel;
        this.payload = payload;
    }

    // Getters
    public byte getMessageType() { return messageType; }
    public String getApplicationId() { return applicationId; }
    public String getClientId() { return clientId; }
    public String getMessageId() { return messageId; }
    public long getSequenceNumber() { return sequenceNumber; }
    public long getTimestamp() { return timestamp; }
    public int getLogLevel() { return logLevel; }
    public String getPayload() { return payload; }
}