package com.dut.network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;

/**
 * Triển khai INetworkConnection sử dụng Java Blocking I/O.
 * Xử lý triệt để bài toán phân mảnh dữ liệu TCP bằng Length-Prefixed Framing.
 */
public class BlockingTCPConnection implements INetworkConnection {
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    
    // Ngưỡng giới hạn kích thước gói tin tối đa (Ví dụ: 10MB) để chống tấn công tràn RAM (OOM)
    private static final int MAX_FRAME_SIZE = 10 * 1024 * 1024;

    public BlockingTCPConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
    }

    @Override
    public synchronized void send(byte[] payload) throws IOException {
        if (payload.length > MAX_FRAME_SIZE) {
            throw new IllegalArgumentException("Kích thước Payload vượt quá hạn mức cho phép: " + MAX_FRAME_SIZE);
        }
        
        // 1. Ghi Header: 4 bytes biểu diễn chiều dài payload
        out.writeInt(payload.length);
        
        // 2. Ghi Payload thực tế
        out.write(payload);
        
        // 3. Đẩy dữ liệu lập tức từ bộ đệm ứng dụng xuống card mạng
        out.flush();
    }

    @Override
    public byte[] receive() throws IOException {
        try {
            // 1. Đọc 4 bytes đầu tiên để lấy kích thước gói tin sắp tới
            int length = in.readInt();
            
            if (length <= 0 || length > MAX_FRAME_SIZE) {
                throw new IOException("Kích thước khung dữ liệu không hợp lệ hoặc bị hỏng: " + length);
            }
            
            // 2. Khởi tạo mảng byte đúng bằng kích thước khai báo
            byte[] payload = new byte[length];
            
            // 3. Đọc dữ liệu: readFully sẽ block luồng cho đến khi gom ĐỦ số bytes yêu cầu (xử lý fragmentation)
            in.readFully(payload);
            
            return payload;
            
        } catch (EOFException e) {
            // Xảy ra khi đầu bên kia ngắt kết nối đột ngột
            return null;
        }
    }

    @Override
    public String getRemoteAddress() {
        if (socket != null && socket.getRemoteSocketAddress() != null) {
            return socket.getRemoteSocketAddress().toString();
        }
        return "Unknown";
    }

    @Override
    public boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }

    @Override
    public void close() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Lỗi khi giải phóng tài nguyên kết nối: " + e.getMessage());
        }
    }
}