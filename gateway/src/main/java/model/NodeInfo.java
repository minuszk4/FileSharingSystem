package model;

public class NodeInfo {
    private String id;
    private String host;
    private int port;
    private boolean online;

    public NodeInfo() {} // constructor mặc định

    public NodeInfo(String id, String host, int port, boolean online) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.online = online;
    }

    // Getter / Setter
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }
}
