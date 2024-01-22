package cz.cvut.fel.leader;

import cz.cvut.fel.BroadcastMessage;
import cz.cvut.fel.DirectMessage;
import cz.cvut.fel.Node;
import cz.cvut.fel.NodeServiceGrpc;
import cz.cvut.fel.model.Address;
import cz.cvut.fel.utils.NodeUtils;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class RemoteLeader extends AbstractLdr{
    public RemoteLeader(Node node) {
        super(node);
    } // The leader node is some node elsewhere

    @Override
    public void sendMessage(BroadcastMessage msg) {
        ManagedChannel channel = NodeUtils.openChannelTo(node.getMyNeighbours().leader);
        NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
        stub.broadcastMessage(msg);
        Node.closeChannelProperly(channel);
    }

    @Override
    public void sendMessage(DirectMessage msg) {
        ManagedChannel channel = NodeUtils.openChannelTo(node.getMyNeighbours().leader);
        NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
        stub.sendMessage(msg);
        Node.closeChannelProperly(channel);
    }

    @Override
    public void addAddress(String uname, Address address) {

    }

    @Override
    public boolean find(String recipient) {
        return true; // TODO TODO
    }

    void sendDirectTo(String to, String msg, String author){
        boolean status = checkPresence();
        if (status != true){
            //start election
            //wait long enough to elect new leader/ till elected new leader
        }

        ManagedChannel channel = NodeUtils.openChannelTo(node.getMyNeighbours().leader);
        NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
        DirectMessage dm = DirectMessage.newBuilder().setMessage(msg).setRecipient(to).setAuthor(author).build();
        stub.sendMessage(dm);
    }
}
