package dht;

import bittorrent.TorrentFile;
import core.*;

import java.io.*;
import java.util.*;
public abstract class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum MessageType {
        PING, PONG,
        STORE, STORE_RESPONSE,
        FIND_NODE, FIND_NODE_RESPONSE,
        FIND_VALUE, FIND_VALUE_RESPONSE,
        STORE_PIECE,STORE_PIECE_RESPONSE,
        FIND_PIECE,FIND_PIECES_RESPONSE,
        STORE_METADATA,STORE_METADATA_RESPONSE,
        GET_PIECE,GET_PIECES_RESPONSE,
    }

    protected final String messageId;
    protected final NodeID senderId;
    protected final MessageType type;

    protected Message(MessageType type, NodeID senderId) {
        this.type = type;
        this.senderId = senderId;
        this.messageId = UUID.randomUUID().toString();
    }

    public MessageType getType() { return type; }
    public NodeID getSenderId() { return senderId; }
    public String getMessageId() { return messageId; }

    // Serialize message to bytes
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(this);
        oos.flush();
        return bos.toByteArray();
    }

    // Deserialize message from bytes
    public static Message fromBytes(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bis);
        return (Message) ois.readObject();
    }
}

// PING Message
class PingMessage extends Message implements HttpAware {
    private final int httpPort;

    public PingMessage(NodeID senderId, int httpPort) {
        super(MessageType.PING, senderId);
        this.httpPort = httpPort;
    }

    @Override
    public int getHttpPort() { return httpPort; }
}



// PONG Response
class PongMessage extends Message {
    private final String requestId;

    public PongMessage(NodeID senderId, String requestId) {
        super(MessageType.PONG, senderId);
        this.requestId = requestId;
    }

    public String getRequestId() { return requestId; }
}

// STORE Message
class StoreMessage extends Message implements HttpAware {
    private final NodeID key;
    private final byte[] value;
    private final int httpPort;
    public StoreMessage(NodeID senderId, NodeID key, byte[] value,int httpPort) {
        super(MessageType.STORE, senderId);
        this.key = key;
        this.value = value;
        this.httpPort = httpPort;
    }

    public NodeID getKey() { return key; }
    public byte[] getValue() { return value; }
    @Override
    public byte[] toBytes() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(this);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public int getHttpPort() { return httpPort; }
    // -----------------------------
    // Deserialize từ byte[]
    // -----------------------------
    public static StoreMessage fromBytes(byte[] data) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInputStream in = new ObjectInputStream(bis)) {
            return (StoreMessage) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}

// STORE Response
class StoreResponseMessage extends Message {
    private final String requestId;
    private final boolean success;

    public StoreResponseMessage(NodeID senderId, String requestId, boolean success) {
        super(MessageType.STORE_RESPONSE, senderId);
        this.requestId = requestId;
        this.success = success;
    }

    public String getRequestId() { return requestId; }
    public boolean isSuccess() { return success; }
}

// FIND_NODE Message
class FindNodeMessage extends Message {
    private final NodeID targetId;

    public FindNodeMessage(NodeID senderId, NodeID targetId) {
        super(MessageType.FIND_NODE, senderId);
        this.targetId = targetId;
    }

    public NodeID getTargetId() { return targetId; }
}

// FIND_NODE Response
class FindNodeResponseMessage extends Message {
    private final String requestId;
    private final List<Contact> contacts;

    public FindNodeResponseMessage(NodeID senderId, String requestId, List<Contact> contacts) {
        super(MessageType.FIND_NODE_RESPONSE, senderId);
        this.requestId = requestId;
        this.contacts = contacts;
    }

    public String getRequestId() { return requestId; }
    public List<Contact> getContacts() { return contacts; }
}

// FIND_VALUE Message
class FindValueMessage extends Message {
    private final NodeID key;

    public FindValueMessage(NodeID senderId, NodeID key) {
        super(MessageType.FIND_VALUE, senderId);
        this.key = key;
    }

    public NodeID getKey() { return key; }
}

// FIND_VALUE Response
class FindValueResponseMessage extends Message {
    private final String requestId;
    private final byte[] value;
    private final List<Contact> contacts;

    public FindValueResponseMessage(NodeID senderId, String requestId,
                                    byte[] value, List<Contact> contacts) {
        super(MessageType.FIND_VALUE_RESPONSE, senderId);
        this.requestId = requestId;
        this.value = value;
        this.contacts = contacts;
    }

    public String getRequestId() { return requestId; }
    public byte[] getValue() { return value; }
    public List<Contact> getContacts() { return contacts; }
    public boolean hasValue() { return value != null; }
}

// ==================== StorePieceMessage.java ====================

class StorePieceMessage extends Message {
    private static final long serialVersionUID = 1L;

    private String pieceKey;
    private byte[] pieceData;
    private int httpPort;

    public StorePieceMessage(NodeID senderId, String pieceKey, byte[] pieceData, int httpPort) {
        super(MessageType.STORE_PIECE,senderId);
        this.pieceKey = pieceKey;
        this.pieceData = pieceData;
        this.httpPort = httpPort;
    }

    public String getPieceKey() { return pieceKey; }
    public byte[] getPieceData() { return pieceData; }
    public int getHttpPort() { return httpPort; }

    @Override
    public String toString() {
        return "StorePieceMessage{key=" + pieceKey + ", size=" + (pieceData != null ? pieceData.length : 0) + " bytes}";
    }
}

// ==================== StorePieceResponseMessage ====================
 class StorePieceResponseMessage extends Message {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private boolean success;

    public StorePieceResponseMessage(NodeID senderId, String requestId, boolean success) {
        super(MessageType.STORE_PIECE_RESPONSE,senderId);
        this.requestId = requestId;
        this.success = success;
    }

    public String getRequestId() { return requestId; }
    public boolean isSuccess() { return success; }

    @Override
    public String toString() {
        return "StorePieceResponseMessage{requestId=" + requestId + ", success=" + success + "}";
    }
}

// ==================== GetPieceMessage====================;

class GetPieceMessage extends Message {
    private static final long serialVersionUID = 1L;

    private String pieceKey;
    private int httpPort;

    public GetPieceMessage(NodeID senderId, String pieceKey, int httpPort) {
        super(MessageType.GET_PIECE,senderId);
        this.pieceKey = pieceKey;
        this.httpPort = httpPort;
    }

    public String getPieceKey() { return pieceKey; }
    public int getHttpPort() { return httpPort; }

    @Override
    public String toString() {
        return "GetPieceMessage{key=" + pieceKey + "}";
    }
}

// ==================== GetPieceResponseMessage ====================


 class GetPieceResponseMessage extends Message {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private String pieceKey;
    private byte[] pieceData;

    public GetPieceResponseMessage(NodeID senderId, String requestId, String pieceKey, byte[] pieceData) {
        super(MessageType.GET_PIECES_RESPONSE,senderId);
        this.requestId = requestId;
        this.pieceKey = pieceKey;
        this.pieceData = pieceData;
    }

    public String getRequestId() { return requestId; }
    public String getPieceKey() { return pieceKey; }
    public byte[] getPieceData() { return pieceData; }
    public boolean hasData() { return pieceData != null && pieceData.length > 0; }

    @Override
    public String toString() {
        return "GetPieceResponseMessage{key=" + pieceKey + ", hasData=" + hasData() +
                ", size=" + (pieceData != null ? pieceData.length : 0) + " bytes}";
    }
}

// ==================== StoreMetadataMessage ====================

class StoreMetadataMessage extends Message {
    private static final long serialVersionUID = 1L;

    private TorrentFile metadata;
    private int httpPort;

    public StoreMetadataMessage(NodeID senderId, TorrentFile metadata, int httpPort) {
        super(MessageType.STORE_METADATA,senderId);
        this.metadata = metadata;
        this.httpPort = httpPort;
    }

    public TorrentFile getMetadata() { return metadata; }
    public int getHttpPort() { return httpPort; }

    @Override
    public String toString() {
        return "StoreMetadataMessage{infoHash=" + (metadata != null ? metadata.getInfoHash() : "null") + "}";
    }
}




class StoreMetadataResponseMessage extends Message {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private boolean success;

    public StoreMetadataResponseMessage(NodeID senderId, String requestId, boolean success) {
        super(MessageType.STORE_METADATA_RESPONSE,senderId);
        this.requestId = requestId;
        this.success = success;
    }

    public String getRequestId() { return requestId; }
    public boolean isSuccess() { return success; }

    @Override
    public String toString() {
        return "StoreMetadataResponseMessage{requestId=" + requestId + ", success=" + success + "}";
    }
}