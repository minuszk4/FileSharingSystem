package dht;

import bittorrent.TorrentFile;
import core.*;

import java.io.IOException;
import java.io.*;
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
    private ServerSocket tcpServer;

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
            executor.submit(this::startTCPListener);

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
                case STORE_PIECE -> {
                    handleStorePiece((StorePieceMessage) message, sender);
                    return; // đã gửi response trong handleStorePiece
                }
                case GET_PIECE -> {
                    handleGetPiece((GetPieceMessage) message, sender);
                    return; // đã gửi response trong handleGetPiece
                }
                case STORE_METADATA -> {
                    handleStoreMetadata((StoreMetadataMessage) message, sender);
                    return; // đã gửi response trong handleStoreMetadata
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
                message.getType() == Message.MessageType.FIND_VALUE_RESPONSE ||
                message.getType() == Message.MessageType.GET_PIECES_RESPONSE||
                message.getType() == Message.MessageType.STORE_METADATA_RESPONSE;


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


        private void handleStorePiece(StorePieceMessage message, Contact sender) {
            try {
                String pieceKey = message.getPieceKey();
                byte[] pieceData = message.getPieceData();

                System.out.println("[Server] Received STORE_PIECE from " + sender.getIp() +
                        " for key: " + pieceKey + " (" + pieceData.length + " bytes)");

                // Store piece locally
                node.getPieceManager().savePieceData(pieceKey, pieceData);

                System.out.println("✅ [Server] Stored piece: " + pieceKey);

                // Send success response
                StorePieceResponseMessage response = new StorePieceResponseMessage(
                        node.getLocalNodeId(),
                        message.getMessageId(),
                        true
                );

                sendResponse(response, sender);

                // Update routing table
                node.getRoutingTable().addContact(sender);

            } catch (Exception e) {
                System.err.println("❌ [Server] Failed to store piece: " + e.getMessage());
                e.printStackTrace();

                // Send failure response
                try {
                    StorePieceResponseMessage response = new StorePieceResponseMessage(
                            node.getLocalNodeId(),
                            message.getMessageId(),
                            false
                    );
                    sendResponse(response, sender);
                } catch (Exception ex) {
                    System.err.println("Failed to send error response: " + ex.getMessage());
                }
            }
        }


        private void handleGetPiece(GetPieceMessage message, Contact sender) {
            try {
                String pieceKey = message.getPieceKey();

                System.out.println("[Server] Received GET_PIECE from " + sender.getIp() +
                        " for key: " + pieceKey);

                // Load piece from local storage
                byte[] pieceData = node.getPieceManager().loadPieceData(pieceKey);

                if (pieceData != null) {
                    System.out.println("✅ [Server] Found piece: " + pieceKey + " (" + pieceData.length + " bytes)");

                    // Send piece data
                    GetPieceResponseMessage response = new GetPieceResponseMessage(
                            node.getLocalNodeId(),
                            message.getMessageId(),
                            pieceKey,
                            pieceData
                    );

                    sendResponse(response, sender);
                } else {
                    System.out.println("⚠ [Server] Piece not found: " + pieceKey);

                    // Send empty response
                    GetPieceResponseMessage response = new GetPieceResponseMessage(
                            node.getLocalNodeId(),
                            message.getMessageId(),
                            pieceKey,
                            null
                    );

                    sendResponse(response, sender);
                }

                // Update routing table
                node.getRoutingTable().addContact(sender);

            } catch (Exception e) {
                System.err.println("❌ [Server] Error handling GET_PIECE: " + e.getMessage());
                e.printStackTrace();

                // Send error response
                try {
                    GetPieceResponseMessage response = new GetPieceResponseMessage(
                            node.getLocalNodeId(),
                            message.getMessageId(),
                            message.getPieceKey(),
                            null
                    );
                    sendResponse(response, sender);
                } catch (Exception ex) {
                    System.err.println("Failed to send error response: " + ex.getMessage());
                }
            }
        }

        /**
         * Handle STORE_METADATA request
         */
    private void handleStoreMetadata(StoreMetadataMessage message, Contact sender) {
        try {
            TorrentFile metadata = message.getMetadata();

            System.out.println("[Server] Received STORE_METADATA from " + sender.getIp() +
                    " for file: " + metadata.getInfoHash());

            // Store metadata locally
            //
            node.getMetadataStore().storeMetadata(metadata);

            System.out.println("✅ [Server] Stored metadata: " + metadata.getInfoHash());

            // Send success response
            StoreMetadataResponseMessage response = new StoreMetadataResponseMessage(
                    node.getLocalNodeId(),
                    message.getMessageId(),
                    true
            );

            sendResponse(response, sender);

            // Update routing table
            node.getRoutingTable().addContact(sender);

        } catch (Exception e) {
            System.err.println("❌ [Server] Failed to store metadata: " + e.getMessage());
            e.printStackTrace();

            // Send failure response
            try {
                StoreMetadataResponseMessage response = new StoreMetadataResponseMessage(
                        node.getLocalNodeId(),
                        message.getMessageId(),
                        false
                );
                sendResponse(response, sender);
            } catch (Exception ex) {
                System.err.println("Failed to send error response: " + ex.getMessage());
            }
        }
    }


//    private void sendResponse(Message response, Contact recipient) throws IOException {
//        byte[] data = response.toBytes();
//
//        // Check if too large for UDP
//        if (data.length > 60000) {
//            System.out.println("⚠ Large response (" + data.length + " bytes), using TCP");
//            sendViaTCP(response, recipient);
//        } else {
//            DatagramPacket packet = new DatagramPacket(
//                    data,
//                    data.length,
//                    recipient.getAddress().getAddress(),
//                    recipient.getAddress().getPort()
//            );
//            socket.send(packet);
//            System.out.println("✅ Sent response to " + recipient.getIp());
//        }
//    }
    private void sendResponse(Message response, Contact recipient) throws IOException {
        byte[] data = response.toBytes();

        // Chỉ gửi qua UDP
        // TCP response đã được xử lý trong handleTCPConnection()
        DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                recipient.getAddress().getAddress(),
                recipient.getAddress().getPort()
        );
        socket.send(packet);
        System.out.println("✅ Sent UDP response to " + recipient.getIp());
    }
    private void sendViaTCP(Message response, Contact recipient) {
        Socket tcp = null;
        try {
            String addr = recipient.getIp();
            int tcpPort = recipient.getPort(); // port TCP của peer (bên kia lắng nghe ở đây)
            System.out.println("📡 [Server->TCP] Connecting to " + addr + ":" + tcpPort + " to send response " + response.getType());

            tcp = new Socket();
            int connectTimeout = 10000; // ms
            tcp.connect(new InetSocketAddress(addr, tcpPort), connectTimeout);
            tcp.setSoTimeout(10000);

            // Gửi bytes
            byte[] outBytes = response.toBytes();
            OutputStream out = tcp.getOutputStream();
            out.write(outBytes);
            out.flush();
            tcp.shutdownOutput(); // báo bên kia đã gửi xong
            System.out.println("📤 [Server->TCP] Sent " + outBytes.length + " bytes to " + recipient.getIp());

            // (Tùy chọn) đọc dữ liệu trả lời (ack) nếu client trả về qua cùng kết nối
            try (InputStream in = tcp.getInputStream();
                 ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
                byte[] tmp = new byte[8192];
                int len;
                // đọc cho tới EOF (client sẽ shutdownOutput nếu có trả)
                while ((len = in.read(tmp)) != -1) {
                    buf.write(tmp, 0, len);
                }
                byte[] reply = buf.toByteArray();
                if (reply.length > 0) {
                    try {
                        Message maybe = Message.fromBytes(reply);
                        // Nếu reply là 1 response matching, forward cho RPC handler
                        node.getRPC().handleResponse(maybe);
                        System.out.println("⬅️ [Server->TCP] Received reply type " + maybe.getType() + " from " + recipient.getIp());
                    } catch (Exception ex) {
                        System.out.println("⚠ [Server->TCP] Received non-message or failed to parse reply: " + ex.getMessage());
                    }
                }
            } catch (SocketTimeoutException ste) {
                // không có reply từ client — ok, đây không phải lỗi bắt buộc
                System.out.println("⏱ [Server->TCP] No follow-up reply from " + recipient.getIp());
            }

        } catch (ConnectException ce) {
            System.err.println("🚫 [Server->TCP] Cannot connect to " + recipient.getIp() + ":" + recipient.getPort() + " -> " + ce.getMessage());
        } catch (SocketTimeoutException te) {
            System.err.println("⏱ [Server->TCP] Timeout when connecting/reading from " + recipient.getIp() + ":" + recipient.getPort());
        } catch (IOException ioe) {
            System.err.println("❌ [Server->TCP] I/O error sending response to " + recipient.getIp() + ":" + recipient.getPort() + " -> " + ioe.getMessage());
        } finally {
            if (tcp != null && !tcp.isClosed()) {
                try { tcp.close(); } catch (IOException ignored) {}
            }
        }
    }

    private void startTCPListener() {
        try {
            tcpServer = new ServerSocket(port);
            System.out.println("🟢 Kademlia TCP server listening on port " + port);

            while (running) {
                Socket clientSocket = tcpServer.accept();
                executor.submit(() -> handleTCPConnection(clientSocket));
            }

        } catch (IOException e) {
            if (running) {
                System.err.println("❌ TCP listener error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    private void handleTCPConnection(Socket socket) {
        try (socket;
             InputStream in = socket.getInputStream();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

            byte[] tmp = new byte[8192];
            int len;
            while ((len = in.read(tmp)) != -1) {
                buffer.write(tmp, 0, len);
            }

            byte[] data = buffer.toByteArray();
            Message message = Message.fromBytes(data);

            System.out.println("← [TCP] Received " + message.getType() + " (" + data.length + " bytes)");

            // Xử lý message tương tự UDP
            Contact sender = new Contact(
                    message.getSenderId(),
                    socket.getInetAddress(),
                    port, // không cần UDP port ở đây
                    (message instanceof HttpAware h) ? port : 0
            );

            node.getRoutingTable().addContact(sender);

            Message response = processTCPMessage(message, sender);
            if (response != null) {
                socket.getOutputStream().write(response.toBytes());
                socket.shutdownOutput();
            }

        } catch (Exception e) {
            System.err.println("❌ [TCP] Error handling connection: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private Message processTCPMessage(Message message, Contact sender) throws IOException {
        switch (message.getType()) {
            case STORE_PIECE -> {
                handleStorePiece((StorePieceMessage) message, sender);
                return new StorePieceResponseMessage(node.getLocalNodeId(), message.getMessageId(), true);
            }
            case GET_PIECE -> {
                byte[] pieceData = node.getPieceManager().loadPieceData(((GetPieceMessage) message).getPieceKey());
                return new GetPieceResponseMessage(node.getLocalNodeId(), message.getMessageId(),
                        ((GetPieceMessage) message).getPieceKey(), pieceData);
            }
            case STORE_METADATA -> {
                handleStoreMetadata((StoreMetadataMessage) message, sender);
                return new StoreMetadataResponseMessage(node.getLocalNodeId(), message.getMessageId(), true);
            }
            case GET_PIECES_RESPONSE -> {
                node.getRPC().handleResponse(message);
                return null;
            }
            default -> {
                System.out.println("⚠ [TCP] Unhandled message type: " + message.getType());
                return null;
            }
        }
    }

}
