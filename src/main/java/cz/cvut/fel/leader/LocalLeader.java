package cz.cvut.fel.leader;

import cz.cvut.fel.*;
import cz.cvut.fel.model.Address;
import cz.cvut.fel.utils.NodeUtils;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
@Slf4j(topic = "main_topic")
public class LocalLeader extends AbstractLdr{ // the node itself is a leader
    private Map<String, Address> addressMap = new HashMap<String, Address>();

    public boolean checkPresence(){return true;}

    @Override
    public void sendMessage(BroadcastMessage msg) {
        Message message = Message.newBuilder().setMessage(msg.getMessage()).setAuthor(msg.getAuthor()).setIsBcast(true).build();

        for (Address address : addressMap.values()) {
            try {
                ManagedChannel channel = NodeUtils.openChannelTo(address);
                NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
                stub.receiveMessage(message);
                Node.closeChannelProperly(channel);
            } catch (Exception e) {
                log.error("Error sending broadcast message to " + address + " : " + e.getMessage());
            }
        }
    }

    @Override
    public void sendMessage(DirectMessage msg) {
        try{
            ManagedChannel channel = NodeUtils.openChannelTo(addressMap.get(msg.getRecipient()));
            NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
            Message message = Message.newBuilder().setMessage(msg.getMessage()).setAuthor(msg.getAuthor()).setIsBcast(false).build();
            stub.receiveMessage(message);
            Node.closeChannelProperly(channel);
        } catch (Exception e){
            log.error("Error sending direct message to " + msg.getRecipient() + " : " + e.getMessage());
        }

    }

    @Override
    public void addAddress(String uname, Address address) {

    }

    @Override
    public boolean find(String recipient) {
        return addressMap.containsKey(recipient);
        // ping len' TODO
    }

    public LocalLeader(Node node) {
        super(node);
    }
    void sendDirectTo(String to, String msg, String author){

        Address recipient = addressMap.getOrDefault(to, null);
        if (recipient == null){
            //chujna
        }

        ManagedChannel channel = NodeUtils.openChannelTo(recipient);
        NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
        DirectMessage dm = DirectMessage.newBuilder().setMessage(msg).setRecipient(to).setAuthor(author).build();
        stub.sendMessage(dm); 
    }
}
