package core;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NodeConfig {
    @Value("${node.id:#{null}}")
    public String nodeId;

    @Value("${node.host:127.0.0.1}")
    public String host;

    @Value("${node.http.port:8081}")
    public int httpPort;

    @Value("${node.dht.port:4001}")
    public int dhtPort;

    @Value("${node.storage.path:data/chunks}")
    public String storagePath;

    @Value("${node.replication.target:3}")
    public int replicationTarget;
}
