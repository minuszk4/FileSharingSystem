package model;

public class FileInfo {
    private String name;
    private long size;
    private String path;
    private String hash;

    public FileInfo() {} // constructor mặc định

    public FileInfo(String name, long size, String path, String hash) {
        this.name = name;
        this.size = size;
        this.path = path;
        this.hash = hash;
    }

    // Getter / Setter
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
}
