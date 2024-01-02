package cz.cvut.fel;

import cz.cvut.fel.model.Address;
import cz.cvut.fel.model.DSNeighbours;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
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

    private void updateTopology(Address newNodeAddress, StreamObserver<JoinResponse> responseObserver){
        // Respond new node with their neighbrs
        AddressMsg msg_next = myNeighbours.next.toAddressMsg();
        AddressMsg msg_prev = own.toAddressMsg();
        JoinResponse joinResponse = JoinResponse.newBuilder().setNext(msg_next).setPrev(msg_prev).build();
        responseObserver.onNext(joinResponse);
        responseObserver.onCompleted();

        // Set own next to joined node
        myNeighbours.next = newNodeAddress.copy();
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
        tryJoin(new Address("localhost", 1111));
        new Thread(chatClient).start();
    }

    public void tryJoin(Address to){
        if (to.compareTo(own) == 0){
            System.out.println("Self join requested, seizing");
            return;
        }
        try{

            ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 1111)
                    .usePlaintext()
                    .build();
            NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
            AddressMsg addressMsg = AddressMsg.newBuilder().setIpAddress(own.hostname).setPort(own.port).build();
            JoinRequest request = JoinRequest.newBuilder().setName(uname).setAddress(addressMsg).build();
            JoinResponse response = stub.join(request);
            myNeighbours.set(response);
            // tell my next he has new prev
            channel = ManagedChannelBuilder.forAddress(myNeighbours.next.hostname, myNeighbours.next.port).usePlaintext().build();
            stub = NodeServiceGrpc.newBlockingStub(channel);
            request = JoinRequest.newBuilder().setAddress(own.toAddressMsg()).build();
            Empty empty = stub.updateConnection(request);

        }catch (Exception e){
            System.err.println("Message listener - something is wrong: " + e.getMessage());
        }
    }

    public void join(JoinRequest request, StreamObserver<JoinResponse> responseObserver) {
        System.out.println("Received join request from: " + request.getName());
        Address externalAddress = new Address(request.getAddress());
        if (myNeighbours.next.compareTo(own) == 0 && myNeighbours.prev.compareTo(own) == 0){
            System.out.println("First join request, setting neighbours");
            AddressMsg next = own.toAddressMsg();
            AddressMsg prev = own.toAddressMsg();
            myNeighbours.next = externalAddress.copy();
            myNeighbours.prev = externalAddress.copy();
            responseObserver.onNext(JoinResponse.newBuilder().setNext(next).setPrev(prev).build());
            responseObserver.onCompleted();
        } else {
            updateTopology(externalAddress, responseObserver);
        }
    }

    public void updatePrev(JoinRequest msg) {
        myNeighbours.prev = new Address(msg.getAddress());
    }

    public void sendBroadcastMsg(String commandline) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(myNeighbours.next.hostname, myNeighbours.next.port)
                .usePlaintext()
                .build();
        NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
        BroadcastMessage message = BroadcastMessage.newBuilder().setMessage(commandline).setAuthor(uname).build();
        stub.broadcastMessage(message);
    }
    public void passBroadcastMsg(BroadcastMessage msg){
        if (msg.getAuthor().equals(uname)){
            return;
        }
        ManagedChannel channel = ManagedChannelBuilder.forAddress(myNeighbours.next.hostname, myNeighbours.next.port)
                .usePlaintext()
                .build();
        NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
        stub.broadcastMessage(msg);
    }

    public void processMessage(DirectMessage message) {
        if (message.getRecipient().equals(uname)){
            chatClient.receiveDirectMsg(message);
            message = message.toBuilder().setReceived(true).build();
        }
        if (message.getAuthor().equals(uname)){
            if (!message.getReceived()){
                chatClient.failedDirectMsg(message);
            }
            return;
        }
        ManagedChannel channel = ManagedChannelBuilder.forAddress(myNeighbours.next.hostname, myNeighbours.next.port)
                .usePlaintext()
                .build();
        NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
        stub.sendMessage(message);
    }

    public void sendDirectMsg(String commandline) {
        String[] split = commandline.split(" ");
        if (split.length < 3){
            System.out.println("Usage: /dm <recipient> <message>");
            return;
        }
        String recipient = split[1];
        String message = split[2];
        ManagedChannel channel = ManagedChannelBuilder.forAddress(myNeighbours.next.hostname, myNeighbours.next.port)
                .usePlaintext()
                .build();
        NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
        DirectMessage msg = DirectMessage.newBuilder().setMessage(message).setAuthor(uname).setRecipient(recipient).setReceived(false).build();
        stub.sendMessage(msg);
    }
}
