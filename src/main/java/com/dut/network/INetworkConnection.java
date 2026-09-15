package com.dut.network;

import java.io.IOException;

/**
 * Interface trừu tượng hóa tầng Mạng.
 * Giúp Log Agent và Log Server không phụ thuộc cứng vào Socket I/O thông thường.
 */
public interface INetworkConnection extends AutoCloseable {
    /**
     * Gửi một mảng byte nhị phân đã đóng gói qua mạng.
     */
    void send(byte[] payload) throws IOException;

    /**
     * Đọc một mảng byte nhị phân hoàn chỉnh (Đã xử lý TCP Framing).
     * @return mảng byte payload, hoặc null nếu kết nối bị ngắt.
     */
    byte[] receive() throws IOException;
    
    /**
     * Lấy địa chỉ IP/Endpoint của đối phương.
     */
    String getRemoteAddress();
    
    /**
     * Kiểm tra trạng thái kết nối hiện tại.
     */
    boolean isConnected();
}