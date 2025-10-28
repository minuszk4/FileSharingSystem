package dht;

import bittorrent.MetadataStore;
import bittorrent.PeerServer;
import bittorrent.PieceManager;
import core.*;

import java.io.IOException;
import java.io.Serializable;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class KademliaNode implements Serializable {
    private static final long serialVersionUID = 1L;
    private final NodeID localNodeId;
    private final RoutingTable routingTable;
    private final DataStore dataStore;
    private final KademliaServer server;
    private final KademliaRPC rpc;
    private final int port;
    private final InetAddress localAddress;   // IP node
    private final Contact selfContact;
    private PeerServer peerServer;
    private final int peerPort;

    public static final int ALPHA = 3;
    public static final int K = 20;
    public static final long TIMEOUT_MS = 5000;
    int http_port = Integer.parseInt(System.getenv("HTTP_PORT"));

    public KademliaNode(int port) throws Exception {
        this.port = port;
        this.localAddress = InetAddress.getLocalHost();
        this.localNodeId = NodeID.fromHash(localAddress.getHostAddress() + ":" + port);
        this.routingTable = new RoutingTable(localNodeId);
        this.dataStore = new DataStore();
        this.server = new KademliaServer(this, port);
        this.rpc = new KademliaRPC(this, server.getSocket());
        this.peerPort = port + 1000;
        this.selfContact = new Contact(localNodeId, localAddress, port, http_port);
        System.out.println("Kademlia Node initialized: " + localNodeId + " at " + localAddress.getHostAddress() + ":" + port);
    }

    public int getHttp_port() { return http_port; }

    public void startPeerServer(PieceManager pieceManager, MetadataStore metadataStore) throws IOException {
        if (peerServer != null) throw new IllegalStateException("Peer server already started");
        peerServer = new PeerServer(peerPort, pieceManager, metadataStore);
        peerServer.start();
        System.out.println("✓ Peer Server started on port " + peerPort);
    }

    public void stopPeerServer() {
        if (peerServer != null) {
            peerServer.stop();
            peerServer = null;
        }
    }

    public void start() {
        server.start();
        System.out.println("Kademlia Node started on port: " + port);
    }

    public void stop() {
        server.stop();
        rpc.shutdown();
        System.out.println("Kademlia Node stopped");
    }

    public void bootstrap(String bootstrapIp, int bootstrapPort) throws Exception {
        NodeID bootstrapNodeId = NodeID.fromHash(bootstrapIp + ":" + bootstrapPort);
        Contact bootstrapContact = new Contact(bootstrapNodeId, InetAddress.getByName(bootstrapIp), bootstrapPort, http_port);

        System.out.println("Bootstrapping to: " + bootstrapIp + ":" + bootstrapPort);
        Contact actualContact = rpc.ping(bootstrapContact);

        if (actualContact != null) {
            routingTable.addContact(actualContact);
            nodeLookup(localNodeId);
            System.out.println("Successfully bootstrapped to network");
        } else {
            throw new Exception("Failed to connect to bootstrap node");
        }
    }

    // Store generic key-value
    public void store(NodeID key, byte[] value) {
        List<Contact> closestNodes = nodeLookup(key);
        int successCount = 0;
        for (Contact contact : closestNodes) {
            if (rpc.store(contact, key, value)) successCount++;
        }
        System.out.println("Stored key " + key + " on " + successCount + " nodes");
    }

    // Find value in DHT
    public byte[] findValue(NodeID key) {
        byte[] localValue = dataStore.get(key);
        if (localValue != null) return localValue;

        Set<Contact> queried = new HashSet<>();
        Set<Contact> toQuery = new HashSet<>(routingTable.findClosestContacts(key, ALPHA));
        byte[] result = null;

        while (!toQuery.isEmpty() && result == null) {
            List<Future<FindValueResult>> futures = new ArrayList<>();
            for (Contact contact : toQuery) {
                if (!queried.contains(contact)) {
                    futures.add(rpc.findValueAsync(contact, key));
                    queried.add(contact);
                }
            }
            toQuery.clear();

            for (Future<FindValueResult> future : futures) {
                try {
                    FindValueResult fvResult = future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (fvResult.hasValue()) {
                        result = fvResult.getValue();
                        break;
                    } else {
                        for (Contact c : fvResult.getContacts()) {
                            if (!queried.contains(c)) toQuery.add(c);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return result;
    }

    // Node lookup
    public List<Contact> nodeLookup(NodeID target) {
        Set<Contact> queried = new HashSet<>();
        Set<Contact> toQuery = new HashSet<>(routingTable.findClosestContacts(target, ALPHA));
        TreeSet<Contact> closest = new TreeSet<>((a,b)-> Integer.compare(a.getNodeId().getDistance(target), b.getNodeId().getDistance(target)));
        closest.addAll(toQuery);

        while (!toQuery.isEmpty()) {
            List<Future<List<Contact>>> futures = new ArrayList<>();
            for (Contact contact : toQuery) {
                if (!queried.contains(contact)) {
                    futures.add(rpc.findNodeAsync(contact, target));
                    queried.add(contact);
                }
            }

            Set<Contact> nextToQuery = new HashSet<>();
            for (Future<List<Contact>> future : futures) {
                try {
                    List<Contact> contacts = future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (contacts != null) {
                        for (Contact c : contacts) {
                            if (!queried.contains(c)) nextToQuery.add(c);
                        }
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
            toQuery = nextToQuery;
        }
        return new ArrayList<>(closest).subList(0, Math.min(K, closest.size()));
    }

    public NodeID getLocalNodeId() { return localNodeId; }
    public RoutingTable getRoutingTable() { return routingTable; }
    public DataStore getDataStore() { return dataStore; }
    public int getPort() { return port; }
    public KademliaRPC getRPC() { return rpc; }

    public void storePeer(String infoHashHex) {
        try {
            byte[] infoHashBytes = hexStringToByteArray(infoHashHex);
            NodeID key = new NodeID(infoHashBytes);
            String value = getLocalIp() + ":" + peerPort;

            List<Contact> closest = routingTable.findClosestContacts(key, K);
            for (Contact contact : closest) {
                try { rpc.store(contact, key, value.getBytes()); }
                catch (Exception e) { System.err.println("Failed to store at " + contact.getIp()); }
            }
        } catch (Exception e) {
            System.err.println("Failed to store peer: " + e.getMessage());
        }
    }

    public List<Contact> findPeers(String infoHashHex) {
        try {
            byte[] infoHashBytes = hexStringToByteArray(infoHashHex);
            NodeID key = new NodeID(infoHashBytes);
            byte[] value = findValue(key);
            if (value != null) return List.of(new Contact(localNodeId, localAddress, peerPort, http_port));
            return nodeLookup(key);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String getLocalIp() {
        try { return InetAddress.getLocalHost().getHostAddress(); }
        catch (Exception e) { return "127.0.0.1"; }
    }

    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte)((Character.digit(s.charAt(i),16)<<4) + Character.digit(s.charAt(i+1),16));
        }
        return data;
    }

    public static class FindValueResult {
        private final byte[] value;
        private final List<Contact> contacts;
        public FindValueResult(byte[] value, List<Contact> contacts) { this.value = value; this.contacts = contacts; }
        public boolean hasValue() { return value != null; }
        public byte[] getValue() { return value; }
        public List<Contact> getContacts() { return contacts; }
    }
}
