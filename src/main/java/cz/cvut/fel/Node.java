package cz.cvut.fel;

import com.google.protobuf.DescriptorProtos;
import cz.cvut.fel.model.Address;
import cz.cvut.fel.model.DSNeighbours;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.stub.StreamObservers;
import lombok.Getter;

import java.io.IOException;

@Getter
public class Node implements Runnable{
    private int id;
    private String uname;
    private Address own;
    private DSNeighbours myNeighbours;
    private ChatClient chatClient;
    private NodeService nodeService;
//    private ManagedChannel channel;// ?? outwards
    private Server server;// ?? inwards

    public Node(String uname, Address own){
        this.id = generateId(uname, own);
        this.uname = uname;
        this.own = own.copy();
        this.myNeighbours = new DSNeighbours(own);
    }

    private int generateId(String uname, Address own) {
        return (uname + own.toString()).hashCode();
    }

    public void printStatus() {
        System.out.println("Status: " + this + " with addres " + own);
        System.out.println("    with neighbours " + myNeighbours);
    }

    @Override
    public void run() {
        id = generateId(uname, own);
        chatClient = new ChatClient(this);
        nodeService = new NodeService(this); // service needed? mb not, probably not
        server = ServerBuilder.forPort(own.port)
                .addService(nodeService)
                .build();
        try {
            server.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        tryJoin(new Address("localhost", 2010));
        new Thread(chatClient).start();
    }

    public void tryJoin(Address to){
        if (to.compareTo(own) == 0){
            System.out.println("Self join requested, seizing");
            return;
        }
        try{

            ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 2010)
                    .usePlaintext()
                    .build();
            NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
            AddressMsg addressMsg = AddressMsg.newBuilder().setIpAddress(own.hostname).setPort(own.port).build();
            JoinRequest request = JoinRequest.newBuilder().setName(uname).setAddress(addressMsg).build();
            JoinResponse response = stub.join(request);
            myNeighbours.set(response);
        }catch (Exception e){
            System.err.println("Message listener - something is wrong: " + e.getMessage());
        }
    }

    public void join(JoinRequest request, StreamObserver<JoinResponse> responseObserver) {
        System.out.println("Received join request from: " + request.getName());
        Address externalAddress = new Address(request.getAddress());
//        if (externalAddress.compareTo(own) == 0){ // ZEROTH CASE PROCESSED
//            System.out.println("Self join requested, seizing");
//            responseObserver.onCompleted();
//            return;
//        }
        if (myNeighbours.next.compareTo(own) == 0 && myNeighbours.prev.compareTo(own) == 0){
            System.out.println("First join request, setting neighbours");
            AddressMsg next = own.toAddressMsg();
            AddressMsg prev = own.toAddressMsg();
            myNeighbours.next = externalAddress.copy();
            myNeighbours.prev = externalAddress.copy();
            responseObserver.onNext(JoinResponse.newBuilder().setNext(next).setPrev(prev).build());
            responseObserver.onCompleted();
        }

    }

    public void sendHelloToNext() {
        System.out.println("Sending hello to next neighbour");
        ManagedChannel channel = ManagedChannelBuilder.forAddress(myNeighbours.next.hostname, myNeighbours.next.port)
                .usePlaintext()
                .build();
        NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
        ChatMessage message = ChatMessage.newBuilder().setMessage("Hello from " + uname).setName(uname).build();
        stub.send(message);
    }
}
