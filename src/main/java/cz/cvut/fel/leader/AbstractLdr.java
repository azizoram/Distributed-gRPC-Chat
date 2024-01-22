package cz.cvut.fel.leader;

import cz.cvut.fel.*;
import cz.cvut.fel.model.Address;
import cz.cvut.fel.utils.NodeUtils;
import io.grpc.ManagedChannel;

public abstract class AbstractLdr {

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
}
