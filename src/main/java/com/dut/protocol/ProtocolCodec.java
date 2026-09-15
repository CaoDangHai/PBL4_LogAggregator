package com.dut.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Xử lý mã hóa (Serialize) Object thành mảng Byte
 * và giải mã (Deserialize) mảng Byte thành LogPacket.
 */
public class ProtocolCodec {

    // Magic Bytes nhận diện đầu gói tin (Ví dụ: 'P', '4')
    private static final short MAGIC_BYTES = (short) 0x5034;

    /**
     * Chuyển đổi LogPacket thành mảng byte để truyền qua TCP Connection.
     */
    public static byte[] serialize(LogPacket packet) {
        byte[] appIdBytes = packet.getApplicationId().getBytes(StandardCharsets.UTF_8);
        byte[] clientIdBytes = packet.getClientId().getBytes(StandardCharsets.UTF_8);
        byte[] messageIdBytes = packet.getMessageId().getBytes(StandardCharsets.UTF_8);
        byte[] payloadBytes = packet.getPayload().getBytes(StandardCharsets.UTF_8);

        // Tính toán dung lượng cấp phát cho ByteBuffer
        // Magic(2) + Type(1) + AppIdLen(2)+Bytes + ClientIdLen(2)+Bytes + MsgIdLen(2)+Bytes 
        // + Seq(8) + Time(8) + Level(4) + PayloadLen(4)+Bytes + Checksum(4)
        int bodyLength = 2 + 1 
                 + 2 + appIdBytes.length 
                 + 2 + clientIdBytes.length 
                 + 2 + messageIdBytes.length 
                 + 8 + 8 + 4 
                 + 4 + payloadBytes.length;

        ByteBuffer buffer = ByteBuffer.allocate(bodyLength + 4); // +4 cho Checksum

        // 1. Ghi thông tin cấu trúc
        buffer.putShort(MAGIC_BYTES);
        buffer.put(packet.getMessageType());

        // Ghi chuỗi dạng: [Độ dài chuỗi (2 bytes)] + [Mảng bytes nội dung]
        buffer.putShort((short) appIdBytes.length);
        buffer.put(appIdBytes);

        buffer.putShort((short) clientIdBytes.length);
        buffer.put(clientIdBytes);

        buffer.putShort((short) messageIdBytes.length);
        buffer.put(messageIdBytes);

        buffer.putLong(packet.getSequenceNumber());
        buffer.putLong(packet.getTimestamp());
        buffer.putInt(packet.getLogLevel());

        buffer.putInt(payloadBytes.length);
        buffer.put(payloadBytes);

        // 2. Tính toán Checksum (CRC32) trên toàn bộ body vừa ghi
        byte[] dataToCheck = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(dataToCheck);

        CRC32 crc32 = new CRC32();
        crc32.update(dataToCheck);
        int checksum = (int) crc32.getValue();

        // 3. Ghi Checksum vào cuối gói tin
        ByteBuffer finalBuffer = ByteBuffer.allocate(dataToCheck.length + 4);
        finalBuffer.put(dataToCheck);
        finalBuffer.putInt(checksum);

        return finalBuffer.array();
    }

    /**
     * Giải mã mảng byte thành đối tượng LogPacket.
     */
    public static LogPacket deserialize(byte[] data) throws Exception {
        if (data == null || data.length < 4) {
            throw new IllegalArgumentException("Gói tin đầu vào quá ngắn hoặc rỗng.");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);

        // 1. Kiểm tra Magic Bytes
        short magic = buffer.getShort();
        if (magic != MAGIC_BYTES) {
            throw new SecurityException("Magic Bytes không khớp! Gói tin không hợp lệ.");
        }

        // 2. Tách Checksum ở 4 bytes cuối ra để kiểm tra tính toàn vẹn
        int receivedChecksum = buffer.getInt(data.length - 4);

        // Dùng duplicate() để lấy data tính Checksum MÀ KHÔNG làm thay đổi con trỏ chính
        // Điều này giúp giữ nguyên vị trí con trỏ của 'buffer' gốc ở ngay sau Magic Bytes.
        ByteBuffer checksumBuffer = buffer.duplicate();
        checksumBuffer.rewind();
        byte[] dataToCheck = new byte[data.length - 4];
        checksumBuffer.get(dataToCheck); // Chỉ con trỏ của checksumBuffer bị dịch chuyển

        CRC32 crc32 = new CRC32();
        crc32.update(dataToCheck);
        int calculatedChecksum = (int) crc32.getValue();

        // Kiểm tra tính toàn vẹn của gói tin bằng Checksum
        if (receivedChecksum != calculatedChecksum) {
            throw new SecurityException("Checksum không khớp! Gói tin đã bị hỏng trên đường truyền mạng.");
        }

        // 3. Đọc các trường thông tin 
        // Con trỏ 'buffer' chính hiện đang ở đúng vị trí thứ 3 (để chuẩn bị đọc messageType)
        byte messageType = buffer.get();

        short appIdLen = buffer.getShort();
        byte[] appIdBytes = new byte[appIdLen];
        buffer.get(appIdBytes);
        String applicationId = new String(appIdBytes, StandardCharsets.UTF_8);

        short clientIdLen = buffer.getShort();
        byte[] clientIdBytes = new byte[clientIdLen];
        buffer.get(clientIdBytes);
        String clientId = new String(clientIdBytes, StandardCharsets.UTF_8);

        short msgIdLen = buffer.getShort();
        byte[] msgIdBytes = new byte[msgIdLen];
        buffer.get(msgIdBytes);
        String messageId = new String(msgIdBytes, StandardCharsets.UTF_8);

        long sequenceNumber = buffer.getLong();
        long timestamp = buffer.getLong();
        int logLevel = buffer.getInt();

        int payloadLen = buffer.getInt();
        byte[] payloadBytes = new byte[payloadLen];
        buffer.get(payloadBytes);
        String payload = new String(payloadBytes, StandardCharsets.UTF_8);

        return new LogPacket(messageType, applicationId, clientId, messageId,
                              sequenceNumber, timestamp, logLevel, payload);
    }
}