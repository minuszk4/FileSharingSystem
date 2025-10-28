package dht;

import core.*;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class KademliaRPC {
    private final KademliaNode node;
    private final DatagramSocket socket;
    private final Map<String, CompletableFuture<Message>> pendingRequests;
    private final ExecutorService executor;
    private volatile boolean running;

    public KademliaRPC(KademliaNode node,DatagramSocket socket) throws SocketException {
        this.node = node;
        this.socket = socket;
        this.pendingRequests = new ConcurrentHashMap<>();
        this.executor = Executors.newFixedThreadPool(5);
//        startResponseListener();
    }
    public void handleResponse(Message message) {
        String requestId = getRequestId(message);

        if (requestId != null) {
            CompletableFuture<Message> future = pendingRequests.remove(requestId);
            if (future != null) {
                future.complete(message);
                System.out.println("✓ Response matched for requestId: " + requestId);
            } else {
                System.out.println("⚠ No pending request for requestId: " + requestId);
            }
        }
    }
//    private void startResponseListener() {
//        executor.submit(() -> {
//            byte[] buffer = new byte[65535];
//            running = true;
//            while (running) {
//                try {
//                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
//                    socket.receive(packet);
//
//                    byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
//                    Message message = Message.fromBytes(data);
//
//                    String requestId = getRequestId(message);
//                    if (requestId == null) {
//                        System.out.println("Received message without requestId (likely a request, not response): " + message.getClass().getSimpleName());
//                    } else {
//                        CompletableFuture<Message> future = pendingRequests.remove(requestId);
//                        if (future != null) future.complete(message);
//                    }
//
//                } catch (Exception e) {
//                    if (running) System.err.println("Error in response listener: " + e.getMessage());
//                }
//
//            }
//        });
//    }

    private String getRequestId(Message message) {
        if (message instanceof PongMessage) return ((PongMessage) message).getRequestId();
        if (message instanceof StoreResponseMessage) return ((StoreResponseMessage) message).getRequestId();
        if (message instanceof FindNodeResponseMessage) return ((FindNodeResponseMessage) message).getRequestId();
        if (message instanceof FindValueResponseMessage) return ((FindValueResponseMessage) message).getRequestId();
        return null;
    }

    private Message sendRequest(Message request, Contact contact, long timeoutMs) throws Exception {
        CompletableFuture<Message> future = new CompletableFuture<>();
        pendingRequests.put(request.getMessageId(), future);
        System.out.println("KademliaRPC sending request: " + request.toString());
        try {
            byte[] data = request.toBytes();
            System.out.println(new String(data));
            System.out.println(contact.getAddress().getAddress()+":"+contact.getAddress().getPort());
            try {
                DatagramPacket packet = new DatagramPacket(
                        data,
                        data.length,
                        contact.getAddress().getAddress(),
                        contact.getAddress().getPort()
                );
                System.out.println("Sending request to " + contact.getAddress().getAddress()+":"+contact.getAddress().getPort());
                socket.send(packet);
            } catch (IOException e) {
                System.out.println("Error sending request to " + contact.getAddress().getAddress());
            }


            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pendingRequests.remove(request.getMessageId());
            throw new Exception("Request timeout");
        }
    }

    public Contact ping(Contact contact) {
        try {
            PingMessage request = new PingMessage(node.getLocalNodeId(),node.getHttp_port());
            System.out.println("RPC: sending ping request to " + contact.getAddress() + " : " + request.toString());

            Message response = sendRequest(request, contact, KademliaNode.TIMEOUT_MS);

            if (response != null) {
                System.out.println("RPC: received ping response: " + response.toString());
            }

            if (response instanceof PongMessage) {
                contact.updateLastSeen();
                return contact;
            } else {
                System.out.println("RPC: unexpected response or null");
            }
        } catch (Exception e) {
            contact.incrementFailedRequests();
            e.printStackTrace();
            System.err.println("RPC: ping failed to " + contact.getAddress() + " -> " + e.getMessage());
        }
        return null;
    }


    public boolean store(Contact contact, NodeID key, byte[] value) {
        try {
            System.out.println("RPC: sending store request to " + contact.getAddress() + " : " + value.toString()+"key: "+key.toString());
            StoreMessage request = new StoreMessage(node.getLocalNodeId(), key, value,node.getHttp_port());
            Message response = sendRequest(request, contact, KademliaNode.TIMEOUT_MS);
            return response instanceof StoreResponseMessage && ((StoreResponseMessage) response).isSuccess();
        } catch (Exception e) { return false; }
    }

    public List<Contact> findNode(Contact contact, NodeID targetId) {
        try {
            FindNodeMessage request = new FindNodeMessage(node.getLocalNodeId(), targetId);
            Message response = sendRequest(request, contact, KademliaNode.TIMEOUT_MS);
            if (response instanceof FindNodeResponseMessage) {
                List<Contact> contacts = ((FindNodeResponseMessage) response).getContacts();
                contacts.forEach(node.getRoutingTable()::addContact);
                return contacts;
            }
        } catch (Exception e) {}
        return Collections.emptyList();
    }

    public Future<List<Contact>> findNodeAsync(Contact contact, NodeID targetId) {
        return CompletableFuture.supplyAsync(() -> findNode(contact, targetId), executor);
    }

    public KademliaNode.FindValueResult findValue(Contact contact, NodeID key) {
        try {
            FindValueMessage request = new FindValueMessage(node.getLocalNodeId(), key);
            Message response = sendRequest(request, contact, KademliaNode.TIMEOUT_MS);
            if (response instanceof FindValueResponseMessage fvr) {
                if (fvr.hasValue()) return new KademliaNode.FindValueResult(fvr.getValue(), null);
                fvr.getContacts().forEach(node.getRoutingTable()::addContact);
                return new KademliaNode.FindValueResult(null, fvr.getContacts());
            }
        } catch (Exception e) {}
        return new KademliaNode.FindValueResult(null, Collections.emptyList());
    }

    public Future<KademliaNode.FindValueResult> findValueAsync(Contact contact, NodeID key) {
        return CompletableFuture.supplyAsync(() -> findValue(contact, key), executor);
    }

    public void shutdown() {
        running = false;
        socket.close();
        executor.shutdown();
    }
}
