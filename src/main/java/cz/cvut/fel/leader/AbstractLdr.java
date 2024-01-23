package cz.cvut.fel.leader;

import cz.cvut.fel.*;
import cz.cvut.fel.model.Address;
import cz.cvut.fel.utils.NodeUtils;
import io.grpc.ManagedChannel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j(topic = "main_topic")
public abstract class AbstractLdr {
    protected Map<String, Address> addressMap = new HashMap<String, Address>();

    protected String hash = null;

    protected Node node;
    AbstractLdr(Node node){
        this.node = node;
    }
    /**
     * Checks if current leader is responsive, if no should start reelection
     * @return isResponsive
     */
    boolean checkPresence(){
        Address currentLeader = node.getMyNeighbours().leader;
        try{
            ManagedChannel channel = NodeUtils.openChannelTo(currentLeader);
            NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
            stub.ping(Empty.newBuilder().build());
            channel.shutdown();
            return true;
        } catch (Exception e){
            node.startElection();
            return false;
        }
    }

    public abstract void sendMessage(BroadcastMessage msg);
    public abstract void sendMessage(DirectMessage msg);

    public abstract void nodeHasJoined(NodeJoined nodeJoined);

    public abstract boolean find(String recipient);

    protected String calculateHash(){
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hash = addressMap.entrySet().stream().sorted().map(entry -> entry.getKey() + entry.getValue().toString()).reduce("", String::concat);
            return new String(digest.digest(hash.getBytes()));
        }catch (Exception e){
            log.error("Error calculating hash: " + e.getMessage());
        }
        return String.valueOf(System.currentTimeMillis());
    }
    protected void updateHash() {
        hash = calculateHash();
    }

    public StringMsg getHashMsg() {
        updateHash();
        return StringMsg.newBuilder().setMsg(hash).build();
    }

    public AddressBookMsg getAddressBookMsg() {
        return AddressBookMsg.newBuilder()
                .putAllAddressBook(
                        addressMap.entrySet().stream()
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        entry -> entry.getValue().toAddressMsg()
                                ))
                )
                .build();
    }

    public abstract void updateAddressBook();
    public Map<String, Address> getAddressBook() {
        return new HashMap<>(addressMap);
    }
}
