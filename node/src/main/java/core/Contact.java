package core;

import org.springframework.beans.factory.annotation.Value;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;

public class Contact implements Serializable {
    private static final long serialVersionUID = 1L;

    private final NodeID nodeId;
    private final InetSocketAddress address;
    private long lastSeen;
    private int failedRequests;
    private int httpPort;
    public Contact(NodeID nodeId, InetAddress ip, int port,int httpPort) {
        this.nodeId = nodeId;
        this.address = new InetSocketAddress(ip, port);
        this.lastSeen = System.currentTimeMillis();
        this.failedRequests = 0;
        this.httpPort = httpPort;
    }

    public Contact(NodeID nodeId, InetSocketAddress address) {
        this.nodeId = nodeId;
        this.address = address;
        this.lastSeen = System.currentTimeMillis();
        this.failedRequests = 0;
    }

    // -----------------------------
    // Getters / Setters
    // -----------------------------
    public NodeID getNodeId() {
        return nodeId;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public String getIp() {
        return address.getAddress().getHostAddress();
    }

    public int getPort() {
        return address.getPort();
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
        this.failedRequests = 0;
    }

    public void incrementFailedRequests() {
        this.failedRequests++;
    }

    public int getFailedRequests() {
        return failedRequests;
    }

    public boolean isStale(long timeoutMs) {
        return (System.currentTimeMillis() - lastSeen) > timeoutMs;
    }
    public int getHttpPort() {
        return httpPort;
    }
    // -----------------------------
    // Serialize / Deserialize IP+Port
    // -----------------------------
    public byte[] getAddressBytes() {
        byte[] ipBytes = inetAddressToBytes(address.getAddress());
        byte[] portBytes = portToBytes(address.getPort());
        byte[] data = new byte[ipBytes.length + portBytes.length];
        System.arraycopy(ipBytes, 0, data, 0, ipBytes.length);
        System.arraycopy(portBytes, 0, data, ipBytes.length, portBytes.length);
        return data;
    }

//    public static Contact fromByteArray(NodeID nodeId, byte[] data) throws Exception {
//        byte[] ipBytes = Arrays.copyOfRange(data, 0, 4); // IPv4
//        byte[] portBytes = Arrays.copyOfRange(data, 4, 6);
//        InetAddress ip = bytesToInetAddress(ipBytes);
//        int port = bytesToPort(portBytes);
//        return new Contact(nodeId, ip, port,);
//    }

    // -----------------------------
    // Helper methods
    // -----------------------------
    public static byte[] inetAddressToBytes(InetAddress ip) {
        return ip.getAddress();
    }

    public static InetAddress bytesToInetAddress(byte[] data) throws Exception {
        return InetAddress.getByAddress(data);
    }

    public static byte[] portToBytes(int port) {
        return new byte[] { (byte)((port >> 8) & 0xFF), (byte)(port & 0xFF) };
    }

    public static int bytesToPort(byte[] data) {
        return ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
    }

    // -----------------------------
    // Equals / hashCode / toString
    // -----------------------------
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Contact)) return false;
        Contact other = (Contact) obj;
        return this.nodeId.equals(other.nodeId);
    }

    @Override
    public int hashCode() {
        return nodeId.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Contact{id=%s, addr=%s:%d}",
                nodeId.toString().substring(0, 8), getIp(), getPort());
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    public void setLastSeen(long l) {
        this.lastSeen = l;
    }
}
