package model;

import util.HashUtil;

import java.io.Serializable;
import java.util.Arrays;

public class NodeID implements Comparable<NodeID>, Serializable {
    private static final long serialVersionUID = 1L;
    public static final int ID_LENGTH = 20; // 160 bits = 20 bytes
    private final byte[] id;

    public NodeID(byte[] id) {
        if (id.length != ID_LENGTH) {
            throw new IllegalArgumentException("NodeID must be 160 bits (20 bytes)");
        }
        this.id = Arrays.copyOf(id, ID_LENGTH);
    }
    public NodeID(String id) {
        String hex = HashUtil.sha1(id.getBytes());
        byte [] bytes =  hexStringToByteArray(hex);
        this.id = bytes;
    }
    public static NodeID fromHash(String data) {
        try {
            // HashUtil.sha1 trả về hex string, convert lại byte[]
            String hex = HashUtil.sha1(data.getBytes("UTF-8"));
            byte[] bytes = hexStringToByteArray(hex);
            return new NodeID(bytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create NodeID from hash", e);
        }
    }
    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof NodeID)) return false;
        return Arrays.equals(this.id, ((NodeID) obj).id);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(id);
    }

    @Override
    public int compareTo(NodeID other) {
        for (int i = 0; i < ID_LENGTH; i++) {
            int a = this.id[i] & 0xFF;
            int b = other.id[i] & 0xFF;
            if (a != b) return a - b;
        }
        return 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (byte b : id) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
