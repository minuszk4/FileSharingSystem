package dht;

import core.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RoutingTable {
    public static final int ID_LENGTH_BITS = 160; // 160 bits
    private final NodeID localNodeId;
    private final List<KBucket> buckets;
    private final Map<NodeID, Contact> allContacts;

    public RoutingTable(NodeID localNodeId) {
        this.localNodeId = localNodeId;
        this.buckets = new ArrayList<>();
        this.allContacts = new ConcurrentHashMap<>();

        // Khởi tạo với 1 bucket cho toàn bộ không gian
        buckets.add(new KBucket(0));
    }

    // Thêm contact vào routing table
    public synchronized boolean addContact(Contact contact) {
        if (contact.getNodeId().equals(localNodeId)) {
            return false; // Không thêm chính mình
        }

        int bucketIndex = getBucketIndex(contact.getNodeId());
        KBucket bucket = buckets.get(bucketIndex);

        boolean added = bucket.addContact(contact);

        Contact existing = allContacts.get(contact.getNodeId());
        if (existing != null) {
            // Chỉ update các thông tin hợp lệ
            if (contact.getHttpPort() != -1) existing.setHttpPort(contact.getHttpPort());
            existing.setLastSeen(System.currentTimeMillis());
        } else if (added) {
            allContacts.put(contact.getNodeId(), contact);
        } else if (bucketIndex == buckets.size() - 1) {
            // Bucket cuối cùng đầy, cần split
            splitBucket(bucketIndex);
            return addContact(contact); // Thử lại sau khi split
        }

        return added;
    }


    // Tách bucket thành 2 bucket nhỏ hơn
    private synchronized void splitBucket(int index) {
        if (index != buckets.size() - 1) {
            return; // Chỉ split bucket cuối
        }

        KBucket oldBucket = buckets.get(index);
        int newDepth = oldBucket.getDepth() + 1;

        if (newDepth > ID_LENGTH_BITS) {
            return; // Không thể split thêm
        }

        // Tạo 2 bucket mới
        KBucket bucket0 = new KBucket(newDepth);
        KBucket bucket1 = new KBucket(newDepth);

        // Phân phối lại contacts
        for (Contact contact : oldBucket.getAllContacts()) {
            if (contact.getNodeId().getBit(newDepth - 1) == 0) {
                bucket0.addContact(contact);
            } else {
                bucket1.addContact(contact);
            }
        }

        // Thay thế bucket cũ
        buckets.set(index, bucket0);
        buckets.add(bucket1);
    }

    // Tìm bucket index cho nodeId
    private int getBucketIndex(NodeID nodeId) {
        int distance = localNodeId.getDistance(nodeId);
        int bucketIndex = Math.max(0, distance - 1);
        return Math.min(bucketIndex, buckets.size() - 1);
    }

    // Tìm K contacts gần nhất với target
    public List<Contact> findClosestContacts(NodeID target, int count) {
        List<Contact> all = new ArrayList<>(allContacts.values());
        all.sort((a, b) -> {
            int distA = a.getNodeId().getDistance(target);
            int distB = b.getNodeId().getDistance(target);
            return Integer.compare(distA, distB);
        });
        return all.subList(0, Math.min(count, all.size()));
    }

    // Lấy contact theo NodeID
    public Contact getContact(NodeID nodeId) {
        return allContacts.get(nodeId);
    }

    // Xóa contact
    public synchronized boolean removeContact(NodeID nodeId) {
        Contact contact = allContacts.remove(nodeId);
        if (contact != null) {
            int bucketIndex = getBucketIndex(nodeId);
            buckets.get(bucketIndex).removeContact(contact);
            return true;
        }
        return false;
    }

    // Lấy tất cả contacts
    public List<Contact> getAllContacts() {
        return new ArrayList<>(allContacts.values());
    }

    public int getTotalContacts() {
        return allContacts.size();
    }

    public int getBucketCount() {
        return buckets.size();
    }

    public NodeID getLocalNodeId() {
        return localNodeId;
    }

    @Override
    public String toString() {
        return String.format("RoutingTable{localId=%s, buckets=%d, contacts=%d}",
                localNodeId.toString().substring(0, 8), buckets.size(), allContacts.size());
    }
}