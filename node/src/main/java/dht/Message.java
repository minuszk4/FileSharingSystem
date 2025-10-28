package dht;

import core.*;

import java.io.*;
import java.util.*;
public abstract class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum MessageType {
        PING, PONG,
        STORE, STORE_RESPONSE,
        FIND_NODE, FIND_NODE_RESPONSE,
        FIND_VALUE, FIND_VALUE_RESPONSE
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