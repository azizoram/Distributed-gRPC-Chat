package cz.cvut.fel;

import com.google.protobuf.DescriptorProtos;
import cz.cvut.fel.model.Address;
import cz.cvut.fel.model.DSNeighbours;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.stub.StreamObservers;
import lombok.Getter;

@Getter
public class Node implements Runnable{
    private int id;
    private String uname;
    private Address own;
    private DSNeighbours myNeighbours;
    private ChatClient chatClient;
    private NodeService nodeService;

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
        nodeService = new NodeService(this);
        try{
            ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", own.port)
                    .usePlaintext()
                    .build();
            NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
            AddressMsg addressMsg = AddressMsg.newBuilder().setIpAddress("localhost").setPort(2010).build();
            JoinRequest request = JoinRequest.newBuilder().setName(uname).setAddress(addressMsg).build();
            JoinResponse response = stub.join(request);
            myNeighbours.set(response);

        }catch (Exception e){
            System.err.println("Message listener - something is wrong: " + e.getMessage());
        }
        new Thread(chatClient).start();
    }

    public void join(JoinRequest request) {
        System.out.println("Received join request from: " + request.getName());
        Address externalAddress = new Address(request.getAddress());
        if (externalAddress.equals(own)){
            System.out.println("Self join requested, seizing");
            return;
        }

    }
}
