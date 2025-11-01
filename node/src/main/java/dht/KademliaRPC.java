package dht;

import bittorrent.TorrentFile;
import core.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class KademliaRPC {
    private final KademliaNode node;
    private final DatagramSocket socket;
    private final Map<String, CompletableFuture<Message>> pendingRequests;
    private final ExecutorService executor;
    private volatile boolean running;

    public KademliaRPC(KademliaNode node, DatagramSocket socket) throws SocketException {
        this.node = node;
        this.socket = socket;
        this.pendingRequests = new ConcurrentHashMap<>();
        this.executor = Executors.newFixedThreadPool(5);
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

    private String getRequestId(Message message) {
        if (message instanceof PongMessage) return ((PongMessage) message).getRequestId();
        if (message instanceof StoreResponseMessage) return ((StoreResponseMessage) message).getRequestId();
        if (message instanceof FindNodeResponseMessage) return ((FindNodeResponseMessage) message).getRequestId();
        if (message instanceof FindValueResponseMessage) return ((FindValueResponseMessage) message).getRequestId();
        if (message instanceof StorePieceResponseMessage) return ((StorePieceResponseMessage) message).getRequestId();
        if (message instanceof GetPieceResponseMessage) return ((GetPieceResponseMessage) message).getRequestId();
        if (message instanceof StoreMetadataResponseMessage) return ((StoreMetadataResponseMessage) message).getRequestId();
        if (message instanceof GetPieceResponseMessage) return ((GetPieceResponseMessage) message).getRequestId(); // ← ADD THIS

        return null;
    }

    private Message sendRequest(Message request, Contact contact, long timeoutMs) throws Exception {
        CompletableFuture<Message> future = new CompletableFuture<>();
        pendingRequests.put(request.getMessageId(), future);
        System.out.println("KademliaRPC sending request: " + request.toString());

        try {
            byte[] data = request.toBytes();

            // Check if message is too large for UDP (>60KB)
            if (data.length > 60000) {
                System.out.println("⚠ Large message detected (" + data.length + " bytes), using TCP fallback");
                return sendViaTCP(request, contact, timeoutMs);
            }

            try {
                DatagramPacket packet = new DatagramPacket(
                        data,
                        data.length,
                        contact.getAddress().getAddress(),
                        contact.getAddress().getPort()
                );
                System.out.println("Sending request to " + contact.getAddress().getAddress() + ":" + contact.getAddress().getPort());
                socket.send(packet);
            } catch (IOException e) {
                System.out.println("Error sending request to " + contact.getAddress().getAddress());
                throw e;
            }

            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pendingRequests.remove(request.getMessageId());
            throw new Exception("Request timeout");
        }
    }

    /**
     * TCP fallback for large messages (pieces)
     */
    private Message sendViaTCP(Message request, Contact contact, long timeoutMs) throws Exception {
        try (Socket tcpSocket = new Socket()) {
            tcpSocket.connect(new InetSocketAddress(
                    contact.getAddress().getAddress(),
                    contact.getPort()
            ), (int) timeoutMs);

            // Send request
            tcpSocket.getOutputStream().write(request.toBytes());

            tcpSocket.shutdownOutput();
            System.out.println("[RPC->TCP] Sent request, waiting for response...");

            // Wait for response

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] tmp = new byte[8192];
            int len;

            InputStream in = tcpSocket.getInputStream();
            while ((len = in.read(tmp)) != -1) {
                buffer.write(tmp, 0, len);
            }

            byte[] responseData = buffer.toByteArray();

            if (responseData.length > 0) {
                Message response = Message.fromBytes(responseData);
                System.out.println("✅ [RPC->TCP] Received response: " + response.getType());
                return response;
            }

            throw new Exception("No response received via TCP");
        }
    }

    // ==================== EXISTING METHODS ====================

    public Contact ping(Contact contact) {
        try {
            PingMessage request = new PingMessage(node.getLocalNodeId(), node.getPort());
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
            System.out.println("RPC: sending store request to " + contact.getAddress() + " : " + value.toString() + "key: " + key.toString());
            StoreMessage request = new StoreMessage(node.getLocalNodeId(), key, value, node.getHttp_port());
            Message response = sendRequest(request, contact, KademliaNode.TIMEOUT_MS);
            return response instanceof StoreResponseMessage && ((StoreResponseMessage) response).isSuccess();
        } catch (Exception e) {
            return false;
        }
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
        } catch (Exception e) {
        }
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
        } catch (Exception e) {
        }
        return new KademliaNode.FindValueResult(null, Collections.emptyList());
    }

    public Future<KademliaNode.FindValueResult> findValueAsync(Contact contact, NodeID key) {
        return CompletableFuture.supplyAsync(() -> findValue(contact, key), executor);
    }

    // ==================== NEW PIECE DISTRIBUTION METHODS ====================

    /**
     * Send STORE_PIECE request to remote node
     */
    public boolean sendStorePiece(Contact contact, String pieceKey, byte[] pieceData) {
        try {
            System.out.println("[RPC] Sending STORE_PIECE to " + contact.getIp() + " for key: " + pieceKey + " (" + pieceData.length + " bytes)");

            StorePieceMessage request = new StorePieceMessage(
                    node.getLocalNodeId(),
                    pieceKey,
                    pieceData,
                    node.getPort()
            );

            Message response = sendRequest(request, contact, 30000); // 30s timeout for large pieces

            if (response instanceof StorePieceResponseMessage) {
                boolean success = ((StorePieceResponseMessage) response).isSuccess();
                if (success) {
                    System.out.println("✅ [RPC] Piece stored successfully at " + contact.getIp());
                } else {
                    System.err.println("❌ [RPC] Failed to store piece at " + contact.getIp());
                }
                return success;
            }

            System.err.println("❌ [RPC] Unexpected response type for STORE_PIECE");
            return false;

        } catch (Exception e) {
            System.err.println("❌ [RPC] Error sending STORE_PIECE to " + contact.getIp() + ": "+contact.getPort() + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Send GET_PIECE request to remote node
     */
    public byte[] sendGetPiece(Contact contact, String pieceKey) {
        try {
            System.out.println("[RPC] Requesting piece from " + contact.getIp() + " for key: " + pieceKey);

            GetPieceMessage request = new GetPieceMessage(
                    node.getLocalNodeId(),
                    pieceKey,
                    node.getPort()
            );

            Message response = sendRequest(request, contact, 30000); // 30s timeout

            if (response instanceof GetPieceResponseMessage) {
                GetPieceResponseMessage pieceResponse = (GetPieceResponseMessage) response;

                if (pieceResponse.hasData()) {
                    byte[] data = pieceResponse.getPieceData();
                    System.out.println("✅ [RPC] Retrieved piece from " + contact.getIp() + " (" + data.length + " bytes)");
                    return data;
                } else {
                    System.out.println("⚠ [RPC] Piece not found at " + contact.getIp());
                    return null;
                }
            }

            System.err.println("❌ [RPC] Unexpected response type for GET_PIECE");
            return null;

        } catch (Exception e) {
            System.err.println("❌ [RPC] Error getting piece from " + contact.getIp() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Send STORE_METADATA request to remote node
     */
    public boolean sendStoreMetadata(Contact contact, TorrentFile metadata) {
        try {
            System.out.println("[RPC] Sending metadata to " + contact.getIp() + " for file: " + metadata.getInfoHash());

            StoreMetadataMessage request = new StoreMetadataMessage(
                    node.getLocalNodeId(),
                    metadata,
                    node.getPort()
            );

            Message response = sendRequest(request, contact, KademliaNode.TIMEOUT_MS);

            if (response instanceof StoreMetadataResponseMessage) {
                boolean success = ((StoreMetadataResponseMessage) response).isSuccess();
                if (success) {
                    System.out.println("✅ [RPC] Metadata stored successfully at " + contact.getIp());
                } else {
                    System.err.println("❌ [RPC] Failed to store metadata at " + contact.getIp());
                }
                return success;
            }

            System.err.println("❌ [RPC] Unexpected response type for STORE_METADATA");
            return false;

        } catch (Exception e) {
            System.err.println("❌ [RPC] Error sending metadata to " + contact.getIp() + ": " + e.getMessage());
            return false;
        }
    }

    public void shutdown() {
        running = false;
        socket.close();
        executor.shutdown();
    }
}