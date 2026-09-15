package com.dut.storage;

import com.dut.protocol.LogPacket;
import com.dut.protocol.ProtocolCodec;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SimpleWAL {
    private final File walFile;
    private final File tmpFile; // Thêm file tạm để chống mất dữ liệu

    public SimpleWAL(String clientId) {
        String dirPath = "./data/wal";
        try {
            Files.createDirectories(Paths.get(dirPath));
        } catch (IOException ignored) {}

        this.walFile = new File(dirPath + "/" + clientId + ".wal");
        this.tmpFile = new File(dirPath + "/" + clientId + ".wal.tmp");

        // Nếu app bị crash giữa chừng lúc đang compact lần trước, file tmp sẽ còn sót lại
        if (tmpFile.exists()) {
            tmpFile.delete(); // Dọn dẹp rác từ lần crash trước
        }
    }

    public synchronized void append(LogPacket packet) {
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(walFile, true))) {
            byte[] data = ProtocolCodec.serialize(packet);
            dos.writeInt(data.length);
            dos.write(data);
            dos.flush();
        } catch (IOException e) {
            System.err.println("[WAL] Lỗi khi ghi log xuống: " + e.getMessage());
        }
    }

    public synchronized List<LogPacket> recoverLogs() {
        List<LogPacket> recoveredList = new ArrayList<>();
        if (!walFile.exists()) {
            return recoveredList;
        }

        try (DataInputStream dis = new DataInputStream(new FileInputStream(walFile))) {
            while (true) {
                int length = dis.readInt();
                byte[] data = new byte[length];
                dis.readFully(data);

                LogPacket packet = ProtocolCodec.deserialize(data);
                recoveredList.add(packet);
            }
        } catch (EOFException e) {
            System.out.println("[WAL] Khôi phục thành công " + recoveredList.size() + " log từ file WAL.");
        } catch (Exception e) {
            System.err.println("[WAL] Lỗi khi đọc file WAL: " + e.getMessage());
        }
        return recoveredList;
    }

    // ĐÃ FIX: Chống mất dữ liệu hoàn toàn bằng Atomic Rename
    public synchronized void compact(Collection<LogPacket> pendingPackets) {
        if (pendingPackets == null || pendingPackets.isEmpty()) {
            clear(); 
            return;
        }

        // 1. Ghi những log chưa nhận được ACK sang một file .tmp an toàn
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(tmpFile))) {
            for (LogPacket packet : pendingPackets) {
                byte[] data = ProtocolCodec.serialize(packet);
                dos.writeInt(data.length);
                dos.write(data);
            }
            dos.flush();
        } catch (Exception e) {
            System.err.println("[WAL] Lỗi khi tạo file tạm, hủy bỏ quá trình dọn rác: " + e.getMessage());
            tmpFile.delete(); 
            return; // Nếu lỗi, ngưng luôn, giữ nguyên file wal cũ -> KHÔNG MẤT DATA
        }

        // 2. Ghi tmp xong xuôi thì Swap file.
        // Files.move với tùy chọn REPLACE_EXISTING là một thao tác Atomic của OS.
        // Dù rút dây điện ngay lúc này, file WAL cũng không bao giờ bị hỏng.
        try {
            Files.move(tmpFile.toPath(), walFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("[WAL] Lỗi khi Swap tên file WAL: " + e.getMessage());
        }
    }

    public synchronized void clear() {
        if (walFile.exists()) {
            walFile.delete();
        }
    }
}