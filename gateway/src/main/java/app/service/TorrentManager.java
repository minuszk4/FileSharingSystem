package app.service;


import model.TorrentInfo;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;


import java.io.File;
import java.nio.file.Path;
import java.util.UUID;


@Service
public class TorrentManager {

    public TorrentInfo createTorrentFor(Path filePath) throws Exception {
        String infoHash = UUID.nameUUIDFromBytes(filePath.getFileName().toString().getBytes()).toString().replace("-", "");
        File out = new File("./data/shared/torrents/" + infoHash + ".torrent");
        out.getParentFile().mkdirs();
        FileUtils.writeStringToFile(out, "torrent-metadata-for:" + filePath.getFileName(), "UTF-8");
        return new TorrentInfo(infoHash, out.getAbsolutePath());
    }
}