package cz.cvut.fel.services;

import cz.cvut.fel.*;
import cz.cvut.fel.model.Address;
import cz.cvut.fel.utils.NodeUtils;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import static cz.cvut.fel.Node.closeChannelProperly;
import static cz.cvut.fel.utils.NodeUtils.respondEmpty;

@Slf4j(topic = "main_topic")
public class ChatService extends NodeServiceGrpc.NodeServiceImplBase {
//    public static final int MAX_HOP_COUNT = 10;
//    public static final int MAX_HOP_COUNT = 255;

    Node node;
    public ChatService(Node node) {
        this.node = node;
    }

    public void join(JoinRequest request, StreamObserver<JoinResponse> responseObserver) {
        log.info("Received join request from: " + request.getName());
        node.join(request, responseObserver);
    }

    public void sendMessage(DirectMessage message, StreamObserver<DMStatus> responseObserver) {
        responseObserver.onNext(DMStatus.newBuilder().setMsgReceived(
                node.getLeader().find(message.getRecipient()
                )).build()); // check if sendable
        responseObserver.onCompleted();
        node.getLeader().sendMessage(message);
    }

    private static void sendEmptyResponse(StreamObserver<Empty> responseObserver) {
        responseObserver.onNext(
                Empty.newBuilder().build() // load empty response
        );
        responseObserver.onCompleted(); // launch responses
    }

    public void updateConnection(UpdateNeighbourMsg msg, StreamObserver<Empty> responseObserver){
        sendEmptyResponse(responseObserver);
        if (msg.getIsPrev()){
            log.info("New request to set previous node:" + msg.getAddress());
        }else {
            log.info("New request to next node:" + msg.getAddress());
        }

        node.updateNeigh(msg);
    }
    public void broadcastMessage(BroadcastMessage msg, StreamObserver<Empty> responseObserver){
        // leaderOnlySituation now
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
        node.getLeader().sendMessage(msg);
    }


    public void logOut(LogOutRequest request, StreamObserver<Empty> responseObserver){
        sendEmptyResponse(responseObserver);
        node.processLogOut(request);
    }

    public void sendBroadcastMsg(String commandline) {
        BroadcastMessage message = BroadcastMessage.newBuilder().setMessage(commandline).setAuthor(node.getUname()).build();

        node.getLeader().sendMessage(message);
    }

    public void sendDirectMsg(String commandline) {

    }

    public void receiveMessage(Message msg, StreamObserver<Empty> emptyStreamObserver){

        emptyStreamObserver.onNext(Empty.newBuilder().build());
        emptyStreamObserver.onCompleted();
    }

    public void ping(Empty empty, StreamObserver<Empty> responseObserver) {
        respondEmpty(responseObserver);
    }
    public void topologyBroken(boolean isPrevBroken) {
        ManagedChannel channel;
        if (isPrevBroken) {
            channel = NodeUtils.openChannelToNext(node, true);
        } else {
            channel = NodeUtils.openChannelToPrev(node, true);
        }

        if (node.getPrevAddr().compareTo(node.getNextAddr()) == 0 && node.getPrevAddr().compareTo( node.getOwn() ) == 0){
            log.info("Node is isolated out of the network!");
            return;
        }

        NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
        try {
            stub.ping(Empty.newBuilder().build());
        }catch (Exception e){
            if (isPrevBroken) {
                node.nextBroken();
            } else {
                node.prevBroken();
            }
            return;
        }
        BrokenTopology msg = BrokenTopology.newBuilder().setIsPrevBroken(isPrevBroken).setBrokenNode(node.getOwn().toAddressMsg()).build();
        stub.connectionLost(msg);
        closeChannelProperly(channel);
    }

    public void connectionLost(BrokenTopology msg, StreamObserver<Empty> responseObserver) {
        sendEmptyResponse(responseObserver);
        if (msg.getBrokenNode().equals(node.getOwn().toAddressMsg())){
            return;
        }
        ManagedChannel channel;
        if (msg.getIsPrevBroken()){
            channel = NodeUtils.openChannelToNext(node, true);
        }else {
            channel = NodeUtils.openChannelToPrev(node, true);
        }

        if (channel == null){
            node.getMyNeighbours().setLostAsNeighbour(msg);
            try {
                Address lost = new Address(msg.getBrokenNode());
                channel = ManagedChannelBuilder.forAddress(lost.hostname, lost.port)
                        .usePlaintext()
                        .build();
                NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
                UpdateNeighbourMsg updateNeighbourMsg = UpdateNeighbourMsg.newBuilder().setAddress(node.getOwn().toAddressMsg()).setIsPrev(msg.getIsPrevBroken()).build();
                stub.updateConnection(updateNeighbourMsg);
            } catch (Exception e) {
                log.error("Topology is severely broken, cannot update neighbours");
            }
        }else {
            NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
            stub.connectionLost(msg);
            closeChannelProperly(channel);
        }
    }
}
