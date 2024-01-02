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

    public void sendMessage(DirectMessage message, StreamObserver<Empty> responseObserver) {
        node.processMessage(message);
        sendEmptyResponse(responseObserver);
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
//        System.out.println("B!" + msg.getAuthor() + " : " + msg.getMessage());// TODO: REDO
        node.getChatClient().reciveBcastMsg(msg);
        sendEmptyResponse(responseObserver);
        if (msg.getAuthor().equals(node.getUname())){
            return;
        }
        node.passBroadcastMsg(msg);
    }
    public void logOut(LogOutRequest request, StreamObserver<Empty> responseObserver){
        sendEmptyResponse(responseObserver);
        node.processLogOut(request);
    }
}
