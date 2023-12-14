package cz.cvut.fel;

import io.grpc.stub.StreamObserver;

public class NodeService extends NodeServiceGrpc.NodeServiceImplBase {

    Node node;
    public NodeService(Node node) {
        this.node = node;
    }

    public void join(JoinRequest request, StreamObserver<JoinResponse> responseObserver) {
        System.out.println("Received join request from: " + request.getName());
        node.join(request);
//        ChatMessage msg = ChatMessage.newBuilder().setMessage("Welcome " + request.getName()).build();
    }

}
