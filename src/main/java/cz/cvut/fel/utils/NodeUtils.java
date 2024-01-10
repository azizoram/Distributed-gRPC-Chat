package cz.cvut.fel.utils;

import cz.cvut.fel.Empty;
import cz.cvut.fel.Node;
import cz.cvut.fel.model.Address;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

public class NodeUtils {
    public static ManagedChannel openChannelToPrev(Node node, boolean checkConnectivity){
        Address prev = node.getPrevAddr();
        ManagedChannel channel = ManagedChannelBuilder.forAddress(prev.hostname, prev.port)
                .usePlaintext()
                .build();
        if (checkConnectivity && node.isChannelDead(channel)){
            node.prevBroken();
        }
        return channel;
    }

    public static void respondEmpty(StreamObserver<Empty> responseObserver) {
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    public static ManagedChannel openChannelToNext(Node node, boolean checkConnectivity){
        Address next = node.getNextAddr();
        ManagedChannel channel = ManagedChannelBuilder.forAddress(next.hostname, next.port)
                .usePlaintext()
                .build();
        if (checkConnectivity && node.isChannelDead(channel)){
            node.nextBroken();
        }
        return channel;
    }
}
