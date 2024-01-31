package cz.cvut.fel.leader;

import cz.cvut.fel.*;
import cz.cvut.fel.model.Address;
import cz.cvut.fel.utils.NodeUtils;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
@Slf4j(topic = "main_topic")
public class LocalLeader extends AbstractLdr{ // the node itself is a leader

    public LocalLeader(Node node){
        super(node);
        addressMap.put(node.getUname(), node.getOwn());
        updateHash();
    }



    public boolean checkPresence(){return true;}

    @Override
    public void sendMessage(BroadcastMessage msg) {
        Message message = Message.newBuilder().setMessage(msg.getMessage()).setAuthor(msg.getAuthor()).setIsBcast(true).build();

        Iterator<Map.Entry<String, Address>> iter = addressMap.entrySet().iterator();
        while (iter.hasNext()){
            Map.Entry<String, Address> entry = (Map.Entry<String, Address>) iter.next();
            try {
                ManagedChannel channel = NodeUtils.openChannelTo(entry.getValue());
                NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
                stub.receiveMessage(message);
                Node.closeChannelProperly(channel);
            } catch (Exception e) {
                log.error("Error sending broadcast message to " + entry.getValue() + " : " + e.getMessage());
                log.info("Node {} on {} is no longer available, removing", entry.getKey(), entry.getValue());
                iter.remove();
                updateHash();
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
    public void nodeHasJoined(NodeJoined nodeJoined) {
        Address address = new Address(nodeJoined.getAddress());
        addressMap.put(nodeJoined.getUname(), address);
        updateHash();
    }


    @Override
    public boolean find(String recipient) {
        return addressMap.containsKey(recipient);
    }

    @Override
    public void updateAddressBook() {
        // do nothing
    }

}
