package com.dut.client;

import com.dut.protocol.LogPacket;

public class LogQueueItem {
    private final LogPacket packet;
    private int retryCount;
    private long lastSentTimestamp;

    public LogQueueItem(LogPacket packet) {
        this.packet = packet;
        this.retryCount = 0;
        // FIX: Đặt thời gian hiện tại thay vì 0 để không bị Retry spam ngay khi vừa gửi
        this.lastSentTimestamp = System.currentTimeMillis(); 
    }

    public LogPacket getPacket() {
        return packet;
    }

    public int getretryCount() { // Giữ nguyên tên hàm theo code cũ của bạn
        return retryCount;
    }

    public void incrementRetry() {
        this.retryCount++;
        this.lastSentTimestamp = System.currentTimeMillis();
    }

    public long getLastSentTimestamp() {
        return lastSentTimestamp;
    }
}