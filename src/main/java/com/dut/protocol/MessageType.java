package com.dut.protocol;

public class MessageType {
    public static final byte CONNECT = 0x01;
    public static final byte AUTH = 0x02;
    public static final byte LOG = 0x03;
    public static final byte ACK = 0x04;
    public static final byte HEARTBEAT = 0x05;
    public static final byte SYNC_REQUEST = 0x06;
    public static final byte SYNC_RESPONSE = 0x07;
    public static final byte DISCONNECT = 0x08;
    public static final byte ERROR = 0x09;
}