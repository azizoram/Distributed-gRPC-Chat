package cz.cvut.fel;

import io.grpc.stub.StreamObserver;

public class NodeService extends NodeServiceGrpc.NodeServiceImplBase {

    Node node;
    public NodeService(Node node) {
        this.node = node;
    }

    public void join(JoinRequest request, StreamObserver<JoinResponse> responseObserver) {
        System.out.println("123Received join request from: " + request.getName());
        node.join(request, responseObserver);
        JoinResponse response = JoinResponse.newBuilder().setNext(node.getOwn().toAddressMsg()).setPrev(node.getOwn().toAddressMsg()).build();

    }

    public void sendMessage(ChatMessage message, StreamObserver<Empty> responseObserver) {
        System.out.println(message.getAuthor() + " : " + message.getMessage());
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    public void updateConnection(JoinRequest msg, StreamObserver<Empty> responseObserver){
        System.out.println("New previous node:" + msg.getAddress());
        responseObserver.onNext(
                Empty.newBuilder().build() // load empty response
        );
        responseObserver.onCompleted(); // launch responses

        node.updatePrev(msg);
    }
    public void broadcastMessage(BroadcastMessage msg, StreamObserver<Empty> responseObserver){
//        System.out.println("B!" + msg.getAuthor() + " : " + msg.getMessage());// TODO: REDO
        node.getChatClient().reciveBcastMsg(msg);
        responseObserver.onNext(
                Empty.newBuilder().build() // load empty response
        );
        responseObserver.onCompleted(); // launch responses
        if (msg.getAuthor().equals(node.getUname())){
            return;
        }
        node.passBroadcastMsg(msg);
    }
}
