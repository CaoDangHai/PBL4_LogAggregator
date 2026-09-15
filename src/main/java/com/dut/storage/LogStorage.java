package com.dut.storage;

import com.dut.protocol.LogPacket;

public interface LogStorage {
    // Thêm Enum để định nghĩa rõ 3 trạng thái
    enum SaveStatus {
        SUCCESS,    // Lưu mới thành công
        DUPLICATE,  // Trùng lặp (cần gửi lại ACK)
        ERROR       // Lỗi DB (TUYỆT ĐỐI KHÔNG gửi ACK)
    }

    /**
     * Lưu log về kho chứa.
     * @return SaveStatus thay vì boolean.
     */
    SaveStatus saveLog(LogPacket packet);
    
    /**
     * Cập nhật hoặc lưu checkpoint Sequence của Client.
     */
    void updateClientCheckpoint(String clientId, long lastSequence);
    
    long getClientCheckpoint(String clientId);
}