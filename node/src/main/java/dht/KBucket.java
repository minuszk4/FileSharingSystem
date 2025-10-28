package dht;
import core.*;


import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

public class KBucket {
    public static final int K = 20; // Số lượng contact tối đa trong 1 bucket
    private final int depth; // Độ sâu của bucket trong routing table
    private final Deque<Contact> contacts; // LRU cache
    private final Deque<Contact> replacementCache; // Cache thay thế

    public KBucket(int depth) {
        this.depth = depth;
        this.contacts = new ConcurrentLinkedDeque<>();
        this.replacementCache = new ConcurrentLinkedDeque<>();
    }

    // Thêm contact vào bucket
    public synchronized boolean addContact(Contact contact) {
        // Nếu contact đã tồn tại, đưa lên đầu (recently seen)
        if (contacts.remove(contact)) {
            contacts.addFirst(contact);
            return true;
        }

        // Nếu bucket chưa đầy, thêm vào
        if (contacts.size() < K) {
            contacts.addFirst(contact);
            return true;
        }

        // Bucket đầy, thêm vào replacement cache
        replacementCache.addFirst(contact);
        if (replacementCache.size() > K) {
            replacementCache.removeLast();
        }
        return false;
    }

    // Xóa contact
    public synchronized boolean removeContact(Contact contact) {
        boolean removed = contacts.remove(contact);
        if (removed && !replacementCache.isEmpty()) {
            // Thay thế bằng contact từ replacement cache
            contacts.addLast(replacementCache.removeFirst());
        }
        return removed;
    }

    // Lấy contact gần nhất với target
    public synchronized List<Contact> getClosestContacts(NodeID target, int count) {
        List<Contact> all = new ArrayList<>(contacts);
        all.sort((a, b) -> {
            int distA = a.getNodeId().getDistance(target);
            int distB = b.getNodeId().getDistance(target);
            return Integer.compare(distA, distB);
        });
        return all.subList(0, Math.min(count, all.size()));
    }

    // Lấy tất cả contacts
    public synchronized List<Contact> getAllContacts() {
        return new ArrayList<>(contacts);
    }

    // Kiểm tra bucket có đầy không
    public synchronized boolean isFull() {
        return contacts.size() >= K;
    }

    // Lấy contact lâu nhất không hoạt động
    public synchronized Contact getLeastRecentlySeenContact() {
        return contacts.isEmpty() ? null : contacts.getLast();
    }

    public int getDepth() {
        return depth;
    }

    public synchronized int size() {
        return contacts.size();
    }

    @Override
    public String toString() {
        return String.format("KBucket{depth=%d, size=%d/%d}", depth, contacts.size(), K);
    }
}