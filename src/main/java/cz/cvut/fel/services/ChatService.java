package cz.cvut.fel.services;

import cz.cvut.fel.*;
import cz.cvut.fel.utils.NodeUtils;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import static cz.cvut.fel.Node.closeChannelProperly;
import static cz.cvut.fel.utils.NodeUtils.respondEmpty;

@Slf4j(topic = "bimbam")
public class ChatService extends NodeServiceGrpc.NodeServiceImplBase {
    public static final int MAX_HOP_COUNT = 255;
    Node node;
    public ChatService(Node node) {
        this.node = node;
    }

    public void join(JoinRequest request, StreamObserver<JoinResponse> responseObserver) {
        System.out.println("123Received join request from: " + request.getName());
        node.join(request, responseObserver);
        JoinResponse response = JoinResponse.newBuilder().setNext(node.getOwn().toAddressMsg()).setPrev(node.getOwn().toAddressMsg()).build();

    }

    public void sendMessage(DirectMessage message, StreamObserver<Empty> responseObserver) {
        sendEmptyResponse(responseObserver);
        processMessage(message);
    }

    public void processMessage(DirectMessage message) {
        if (message.getRecipient().equals(node.getUname())){
            node.getChatClient().receiveDirectMsg(message);
            message = message.toBuilder().setReceived(true).build();
        }
        if (message.getAuthor().equals(node.getUname())){
            if (!message.getReceived()){
                node.getChatClient().failedDirectMsg(message);
            }
            return;
        }
        if (message.getHopCount() <= 0){
            return;
        }

        message = message.toBuilder().setHopCount(message.getHopCount() - 1).build();

        ManagedChannel channel = NodeUtils.openChannelToNext(node, true);
        NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
        stub.sendMessage(message);
        closeChannelProperly(channel);
    }

    private static void sendEmptyResponse(StreamObserver<Empty> responseObserver) {
        responseObserver.onNext(
                Empty.newBuilder().build() // load empty response
        );
        responseObserver.onCompleted(); // launch responses
    }

    public void updateConnection(JoinRequest msg, StreamObserver<Empty> responseObserver){
        System.out.println("New previous node:" + msg.getAddress());
        sendEmptyResponse(responseObserver);

        node.updatePrev(msg);
    }
    public void broadcastMessage(BroadcastMessage msg, StreamObserver<Empty> responseObserver){
        node.getChatClient().reciveBcastMsg(msg);
        sendEmptyResponse(responseObserver);
        if (msg.getAuthor().equals(node.getUname())){
            return;
        }
        passBroadcastMsg(msg);
    }

    public void passBroadcastMsg(BroadcastMessage msg){
        if (msg.getAuthor().equals(node.getUname())){
            return;
        }
        if (msg.getHopCount() <= 0){
            return;
        }
        ManagedChannel channel = NodeUtils.openChannelToNext(node, true);
        NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
        msg = msg.toBuilder().setHopCount(msg.getHopCount() - 1).build();
        stub.broadcastMessage(msg);
        closeChannelProperly(channel);
    }

    public void logOut(LogOutRequest request, StreamObserver<Empty> responseObserver){
        sendEmptyResponse(responseObserver);
        node.processLogOut(request);
    }

    public void sendBroadcastMsg(String commandline) {
        ManagedChannel channel = NodeUtils.openChannelToNext(node,true);
        NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
        BroadcastMessage message = BroadcastMessage.newBuilder().setMessage(commandline).setAuthor(node.getUname()).setHopCount(MAX_HOP_COUNT).build();
        stub.broadcastMessage(message);
        closeChannelProperly(channel);
    }

    public void sendDirectMsg(String commandline) {
        String[] split = commandline.split(" ");
        if (split.length < 3){
            System.out.println("Usage: /dm <recipient> <message>");
            return;
        }
        String recipient = split[1];
        String message = split[2];
        ManagedChannel channel = NodeUtils.openChannelToNext(node,true);
        NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
        DirectMessage msg = DirectMessage.newBuilder().setMessage(message).setAuthor(node.getUname()).setRecipient(recipient).setReceived(false).setHopCount(MAX_HOP_COUNT).build();
        stub.sendMessage(msg);
        closeChannelProperly(channel);
    }


    public void ping(Empty empty, StreamObserver<Empty> responseObserver) {
        respondEmpty(responseObserver);
    }
    public void topologyBroken(boolean isPrevBroken) {
        if (node.getPrevAddr() == node.getNextAddr() && node.getPrevAddr() == node.getOwn()){
            log.info("Node isolated out of the network");
            return;
        }
        ManagedChannel channel;
        if (isPrevBroken) {
            channel = NodeUtils.openChannelToNext(node, false);
        } else {
            channel = NodeUtils.openChannelToPrev(node, false);
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

    }
}
