package dht;

import core.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DataStore {
    private final Map<NodeID, byte[]> store;
    private final Map<NodeID, Long> timestamps;
    private static final long EXPIRATION_TIME = 24 * 60 * 60 * 1000; // 24 hours

    public DataStore() {
        this.store = new ConcurrentHashMap<>();
        this.timestamps = new ConcurrentHashMap<>();
    }

    // Store value
    public void put(NodeID key, byte[] value) {
        // System.out.println("Storing value for key: " + key.toString());
        store.put(key, value);
        timestamps.put(key, System.currentTimeMillis());
    }

    // Get value
    public byte[] get(NodeID key) {
        // System.out.println("Retrieving value for key: " + key.toString());
        Long timestamp = timestamps.get(key);
        if (timestamp != null && !isExpired(timestamp)) {
            return store.get(key);
        }
        // Expired, remove it
        if (timestamp != null && isExpired(timestamp)) {
            remove(key);
        }
        return null;
    }

    // Remove value
    public void remove(NodeID key) {
        // System.out.println("Removing value for key: " + key.toString());
        store.remove(key);
        timestamps.remove(key);
    }

    // Check if key exists
    public boolean contains(NodeID key) {
        return get(key) != null;
    }

    // Check expiration
    private boolean isExpired(long timestamp) {
        return (System.currentTimeMillis() - timestamp) > EXPIRATION_TIME;
    }

    // Cleanup expired entries
    public void cleanup() {
        long now = System.currentTimeMillis();
        timestamps.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > EXPIRATION_TIME) {
                store.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    public int size() {
        return store.size();
    }
}