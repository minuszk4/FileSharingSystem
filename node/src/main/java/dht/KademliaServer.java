package dht;

import core.*;

import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

public class KademliaServer {
    private final KademliaNode node;
    private final int port;
    private DatagramSocket socket;
    private final ExecutorService executor;
    private volatile boolean running;
    private final  int httpPort = Integer.parseInt(System.getenv("HTTP_PORT"));
    public KademliaServer(KademliaNode node, int port) {
        this.node = node;
        this.port = port;
        this.executor = Executors.newFixedThreadPool(10);
        try {
            socket = new DatagramSocket(port);
        } catch (SocketException e) {
            e.printStackTrace();

        }
    }
    public DatagramSocket getSocket() {
        return socket;
    }
    public void start() {
        try {
            System.out.println("KademliaServer started "+port);
            running = true;

            executor.submit(() -> {


                while (running) {
                    System.out.println("start receiving packet");
                    try {
                        byte[] buffer = new byte[65535];
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);

                        executor.submit(() -> handleMessage(packet));

                    } catch (Exception e) {
                        if (running) System.err.println("Error receiving packet: " + e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            throw new RuntimeException("Failed to start server", e);
        }
    }
    public int getHTTPPort() {
        return httpPort;
    }
    private void handleMessage(DatagramPacket packet) {
        try {
            byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
            Message message = Message.fromBytes(data);

            System.out.println("← Received " + message.getType()
                    + " from " + packet.getAddress() + ":" + packet.getPort());

            // ✅ Kiểm tra: Request hay Response?
            if (isResponse(message)) {
                // Đây là response → forward cho RPC
                node.getRPC().handleResponse(message);
                return;
            }

            // ✅ Đây là request → xử lý tại đây
            int senderHttpPort = (message instanceof HttpAware httpMsg)
                    ? httpMsg.getHttpPort()
                    : packet.getPort() % 2206 + 8082;

            Contact sender = new Contact(
                    message.getSenderId(),
                    packet.getAddress(),
                    packet.getPort(),
                    senderHttpPort
            );

            node.getRoutingTable().addContact(sender);

            Message response = null;

            switch (message.getType()) {
                case PING -> {
                    response = new PongMessage(node.getLocalNodeId(), message.getMessageId());
                }
                case STORE -> {
                    StoreMessage storeMsg = (StoreMessage) message;
                    NodeID key = storeMsg.getKey();
                    byte[] value = storeMsg.getValue();
                    if (key != null && value != null) {
                        node.getDataStore().put(key, value);
                        response = new StoreResponseMessage(node.getLocalNodeId(), message.getMessageId(), true);
                    } else {
                        response = new StoreResponseMessage(node.getLocalNodeId(), message.getMessageId(), false);
                    }
                }
                case FIND_NODE -> {
                    List<Contact> closest = node.getRoutingTable().findClosestContacts(
                            ((FindNodeMessage) message).getTargetId(), KademliaNode.K);
                    response = new FindNodeResponseMessage(
                            node.getLocalNodeId(),
                            message.getMessageId(),
                            new ArrayList<>(closest)
                    );
                }
                case FIND_VALUE -> {
                    FindValueMessage fvMsg = (FindValueMessage) message;
                    byte[] value = node.getDataStore().get(fvMsg.getKey());
                    List<Contact> closest = null;
                    if (value == null) {
                        closest = node.getRoutingTable().findClosestContacts(
                                fvMsg.getKey(), KademliaNode.K);
                    }
                    response = new FindValueResponseMessage(
                            node.getLocalNodeId(),
                            message.getMessageId(),
                            value,
                            closest
                    );
                }
            }

            if (response != null) {
                sendMessage(response, packet.getAddress(), packet.getPort());
            }

        } catch (Exception e) {
            System.err.println("Error handling message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ✅ Helper method
    private boolean isResponse(Message message) {
        return message.getType() == Message.MessageType.PONG ||
                message.getType() == Message.MessageType.STORE_RESPONSE ||
                message.getType() == Message.MessageType.FIND_NODE_RESPONSE ||
                message.getType() == Message.MessageType.FIND_VALUE_RESPONSE;
    }

    private void sendMessage(Message message, InetAddress address, int port) {
        try {
            byte[] data = message.toBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            socket.send(packet);
        } catch (Exception e) {
            System.err.println("Error sending message: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
        executor.shutdown();
    }
}
