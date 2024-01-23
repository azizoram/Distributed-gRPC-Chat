package cz.cvut.fel.leader;

import cz.cvut.fel.*;
import cz.cvut.fel.model.Address;
import cz.cvut.fel.services.ElectionState;
import cz.cvut.fel.services.TerminationService;
import cz.cvut.fel.utils.NodeUtils;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "main_topic")
public class RemoteLeader extends AbstractLdr{
    public RemoteLeader(Node node) {
        super(node);
    } // The leader node is some node elsewhere

    @Override
    public void sendMessage(BroadcastMessage msg) {
        ManagedChannel channel = NodeUtils.openChannelTo(node.getMyNeighbours().leader);
        channel = checkChannel(channel);
        if (channel == null) return;
        NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
        stub.broadcastMessage(msg);
        Node.closeChannelProperly(channel);
    }

    private ManagedChannel checkChannel(ManagedChannel channel) {
        if (node.isChannelDead(channel)){
            log.error("There is no lord here!");
            log.info("Holding message for 10 seconds, hope there will be leader by this time");
            channel = null;
            node.startElection();

            NodeUtils.holdThreadFor(10*1024, (ignored) -> (true));

            channel = NodeUtils.openChannelTo(node.getMyNeighbours().leader);
            if (node.isChannelDead(channel)){
                node.getElectionService().resetState();
                log.error("No leader elected still... Ceasing this message");
                channel = null;
            }
        }
        return channel;
    }

    @Override
    public void sendMessage(DirectMessage msg) {
        ManagedChannel channel = NodeUtils.openChannelTo(node.getMyNeighbours().leader);
        checkChannel(channel);
        if (channel == null) return;
        NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
        stub.sendMessage(msg);
        Node.closeChannelProperly(channel);
    }

    @Override
    public void nodeHasJoined(NodeJoined nodeJoined) {
        try{
            ManagedChannel channel = NodeUtils.openChannelTo(node.getMyNeighbours().leader);
            NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
            stub.zfotalZapisal(nodeJoined);
            Node.closeChannelProperly(channel);
        } catch (Exception e){
            Node.leaderNotFound();
        }
    }

    @Override
    public boolean find(String recipient) {
        ManagedChannel channel = NodeUtils.openChannelTo(node.getMyNeighbours().leader);
        channel = checkChannel(channel);
        if (channel == null) return false;
        TerminationServiceGrpc.TerminationServiceBlockingStub stub = TerminationServiceGrpc.newBlockingStub(channel);
        StringMsg msg = stub.askHash(Empty.newBuilder().build());
        Node.closeChannelProperly(channel);
        handleUpdateAddressBook(msg);
        return addressMap.containsKey(recipient);
    }

    @Override
    public void updateAddressBook() {
        ManagedChannel channel = NodeUtils.openChannelTo(node.getMyNeighbours().leader);
        channel = checkChannel(channel);
        if (channel == null) return;
        TerminationServiceGrpc.TerminationServiceBlockingStub stub = TerminationServiceGrpc.newBlockingStub(channel);
        StringMsg msg = stub.askHash(Empty.newBuilder().build());
        handleUpdateAddressBook(msg);
        Node.closeChannelProperly(channel);
    }

    private void handleUpdateAddressBook(StringMsg msg) {
        if (msg.getMsg().equals(hash)) return;
        addressMap.clear();
        ManagedChannel channel = NodeUtils.openChannelTo(node.getMyNeighbours().leader);
        channel = checkChannel(channel);
        if (channel == null) return;
        TerminationServiceGrpc.TerminationServiceBlockingStub stub = TerminationServiceGrpc.newBlockingStub(channel);
        AddressBookMsg book = stub.askAddressBook(Empty.newBuilder().build());
        book.getAddressBookMap().forEach((key, value) -> addressMap.put(key, new Address(value)));
        updateHash();
        if (!hash.equals(msg.getMsg())) log.debug("Hash mismatch after update!");
        Node.closeChannelProperly(channel);
    }

}
