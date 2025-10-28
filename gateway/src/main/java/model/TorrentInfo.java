package model;

import util.HashUtil;

import java.io.FileNotFoundException;
import java.nio.file.Path;

public class TorrentInfo {
    private String name;
    private String path;
    private String infoHash;

    public TorrentInfo() {} // constructor mặc định

    public TorrentInfo(String name, String path) throws Exception{
        this.name = name;
        this.path = path;
        // Tạo hash giả hoặc tính từ file
        this.infoHash = HashUtil.sha1(path.getBytes());
    }

    // Getter / Setter
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getInfoHash() { return infoHash; }
    public void setInfoHash(String infoHash) { this.infoHash = infoHash; }
}
