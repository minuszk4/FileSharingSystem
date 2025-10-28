package dht;

import dht.*;
import core.*;

public class KademliaNodeTest {
    public static void main(String[] args) throws Exception {
        KademliaNode node1 = new KademliaNode(8001);
        node1.start();
        Thread.sleep(1000);
        KademliaNode node2 = new KademliaNode(8002);
        node2.start();
        node2.bootstrap("127.0.0.1", 8001);

        KademliaNode node3 = new KademliaNode(8003);
        node3.start();
        node3.bootstrap("127.0.0.1", 8001);

        // Lưu giá trị test vào node2
        NodeID key = NodeID.fromHash("hello");
        byte[] value = "world".getBytes();
        node2.store(key, value);

        // Node3 tìm giá trị
        byte[] found = node3.findValue(key);
        if (found != null) {
            System.out.println("Node3 found value: " + new String(found));
        } else {
            System.out.println("Value not found");
        }

        // Tạm dừng một lát để xem log
        Thread.sleep(5000);

        // Stop nodes
        node1.stop();
        node2.stop();
        node3.stop();
    }
}
