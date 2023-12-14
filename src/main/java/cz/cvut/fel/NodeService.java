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
//        responseObserver.onNext(response);
//        ChatMessage msg = ChatMessage.newBuilder().setMessage("Welcome " + request.getName()).build();
    }

    public void send(ChatMessage message, StreamObserver<Empty> responseObserver) {
        System.out.println(message.getName()+ " : " + message.getMessage());
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

}
