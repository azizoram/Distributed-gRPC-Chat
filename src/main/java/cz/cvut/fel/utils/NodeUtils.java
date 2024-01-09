package cz.cvut.fel.utils;

import cz.cvut.fel.Empty;
import cz.cvut.fel.Node;
import cz.cvut.fel.model.Address;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

public class NodeUtils {
    public static ManagedChannel openChannelToPrev(Node node){
        Address prev = node.getPrevAddr();
        ManagedChannel channel = ManagedChannelBuilder.forAddress(prev.hostname, prev.port)
                .usePlaintext()
                .build();
        return channel;
    }

    public static void respondEmpty(StreamObserver<Empty> responseObserver) {
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }
}
