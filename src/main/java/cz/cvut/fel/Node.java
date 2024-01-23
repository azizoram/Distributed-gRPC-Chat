package cz.cvut.fel;

import cz.cvut.fel.leader.AbstractLdr;
import cz.cvut.fel.leader.LocalLeader;
import cz.cvut.fel.leader.RemoteLeader;
import cz.cvut.fel.model.Address;
import cz.cvut.fel.model.DSNeighbours;
import cz.cvut.fel.services.ChatService;
import cz.cvut.fel.services.ElectionService;
import cz.cvut.fel.services.TerminationService;
import cz.cvut.fel.utils.DelayHandler;
import cz.cvut.fel.utils.ElectionUntilDelayHandler;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Getter
@Slf4j(topic = "main_topic")
public class Node implements Runnable{
    private int id;
    private String uname;
    private Address own;
    private DSNeighbours myNeighbours;
    private ChatClient chatClient;
    private ChatService chatService;
    private ElectionService electionService;
    private TerminationService terminationService;
    @Setter
    private DelayHandler delayHandler;
//    private ManagedChannel channel;// ?? outwards
    private Server server;// ?? inwards
    private Thread chatClientThread;
    private AbstractLdr leader; // unset? actualizirovat each time ldr changes
    public Node(String uname, Address own){
        this.id = generateId(uname, own);
        this.uname = uname;
        this.own = own.copy();
        this.myNeighbours = new DSNeighbours(own, this);
        this.delayHandler = new ElectionUntilDelayHandler();
        this.leader = new LocalLeader(this);
    }

    public static void leaderNotFound() {
    }

    private int generateId(String uname, Address own) {
        return (uname + own.toString()).hashCode();
    }

    private void updateTopology(Address newNodeAddress, StreamObserver<JoinResponse> responseObserver){
        // Respond new node with their neighbrs
        AddressMsg msg_next = myNeighbours.next.toAddressMsg();
        AddressMsg msg_prev = own.toAddressMsg();
        JoinResponse joinResponse = JoinResponse.newBuilder().setNext(msg_next).setPrev(msg_prev).setLeader(myNeighbours.leader.toAddressMsg()).build();
        responseObserver.onNext(joinResponse);
        responseObserver.onCompleted();

        // Set own next to joined node
        myNeighbours.next = newNodeAddress.copy();
    }

    public void printStatus() {
        log.info("Status: " + this + " with addres " + own);
        log.info("    with neighbours " + myNeighbours);
        log.info("    node election state is " + electionService.getState());
        log.info("    node computation module is " + (terminationService.isPassive() ? "passive" : "active"));
//        System.out.println("Status: " + this + " with addres " + own);
//        System.out.println("    with neighbours " + myNeighbours);
    }

    @Override
    public void run() {
        id = generateId(uname, own);
        chatClient = new ChatClient(this);
        chatService = new ChatService(this); // service needed? mb not, probably not
        electionService = new ElectionService(this);
        terminationService = new TerminationService(this);
        server = ServerBuilder.forPort(own.port)
                .addService(chatService)
                .addService(electionService)
                .addService(terminationService)
                .build();
        try {
            server.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        (chatClientThread = new Thread(chatClient)).start();
    }

    public void tryJoin(Address to){
        if (to.compareTo(own) == 0){
            System.out.println("Self join requested, seizing");
            return;
        }
        log.info("Trying to join to " + to);
        try{

            ManagedChannel channel = ManagedChannelBuilder.forAddress(to.hostname, to.port)
                    .usePlaintext()
                    .build();
            channel.getState(true);

            NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
            AddressMsg addressMsg = AddressMsg.newBuilder().setIpAddress(own.hostname).setPort(own.port).build();
            JoinRequest request = JoinRequest.newBuilder().setName(uname).setAddress(addressMsg).build();
            delayHandler.handleResponseDelay("join");
            JoinResponse response = stub.join(request);
            myNeighbours.set(response);
            closeChannelProperly(channel);
            notifyLeader();
            // tell my next he has new prev
            channel = ManagedChannelBuilder.forAddress(myNeighbours.next.hostname, myNeighbours.next.port).usePlaintext().build();
            stub = NodeServiceGrpc.newBlockingStub(channel);
            delayHandler.handleResponseDelay("updateConnection");
            UpdateNeighbourMsg rq = UpdateNeighbourMsg.newBuilder().setAddress(own.toAddressMsg()).setIsPrev(true).build();
            stub.updateConnection(rq);

            closeChannelProperly(channel);
            log.info("Successfully joined to " + to);
        }catch (Exception e){
            log.error("Cannot join to " + to + " because of " + e.getMessage());
        }
    }

    public static void closeChannelProperly(ManagedChannel channel) {
        channel.shutdown();
        try {
            // Wait for the channel to be terminated or until a timeout occurs
            if (!channel.awaitTermination(150, TimeUnit.MILLISECONDS)) {
                // Forceful shutdown if it takes too long
                channel.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Thread interrupted while waiting for channel termination", e);
            Thread.currentThread().interrupt(); // Preserve interrupted status
        }
    }
    public void join(JoinRequest request, StreamObserver<JoinResponse> responseObserver) {
        Address externalAddress = new Address(request.getAddress());
        if (myNeighbours.next.compareTo(own) == 0 && myNeighbours.prev.compareTo(own) == 0){
            System.out.println("First join request, setting neighbours");
            AddressMsg next = own.toAddressMsg();
            AddressMsg prev = own.toAddressMsg();
            myNeighbours.next = externalAddress.copy();
            myNeighbours.prev = externalAddress.copy();
            responseObserver.onNext(JoinResponse.newBuilder().setNext(next).setPrev(prev).setLeader(myNeighbours.leader.toAddressMsg()).build());
            responseObserver.onCompleted();
        } else {
            updateTopology(externalAddress, responseObserver);
        }
    }

    public void notifyLeader(){
        //TODO                               
        NodeJoined nodeJoined = NodeJoined.newBuilder().setAddress(own.toAddressMsg()).setUname(uname)
                .setNext(myNeighbours.next.toAddressMsg()).setPrev(myNeighbours.prev.toAddressMsg()).build();
        this.getLeader().nodeHasJoined(nodeJoined);
    }

    public void updateNeigh(UpdateNeighbourMsg msg) {
        if (msg.getIsPrev()) {
            myNeighbours.prev = new Address(msg.getAddress());
        }else {
            myNeighbours.next = new Address(msg.getAddress());
        }
    }

    public void sendBroadcastMsg(String command) {
        chatService.sendBroadcastMsg(command);
    }

    public void sendDirectMsg(String command) { chatService.sendDirectMsg(command); }

    public void selfLogOut(){
        log.info("Self log out");
        log.info("Informing neighbours and isolating, node still functional yet isolated");
        ManagedChannel channel = ManagedChannelBuilder.forAddress(myNeighbours.next.hostname, myNeighbours.next.port)
                .usePlaintext()
                .build();
        NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
//         request = JoinRequest.newBuilder().setAddress(myNeighbours.prev.toAddressMsg()).build();
        LogOutRequest request = LogOutRequest.newBuilder().setOwnAddress(own.toAddressMsg()).setNewNeighbour(myNeighbours.prev.toAddressMsg()).build();
        stub.logOut(request);
        channel = ManagedChannelBuilder.forAddress(myNeighbours.prev.hostname, myNeighbours.prev.port)
                .usePlaintext()
                .build();
        stub = NodeServiceGrpc.newBlockingStub(channel);
        request = LogOutRequest.newBuilder().setOwnAddress(own.toAddressMsg()).setNewNeighbour(myNeighbours.next.toAddressMsg()).build();

        stub.logOut(request);
        closeChannelProperly(channel);
        // isolated
        myNeighbours.next = own.copy();
        myNeighbours.prev = own.copy();
    }

    public void processLogOut(LogOutRequest request) {
        if (request.getOwnAddress().equals(myNeighbours.next.toAddressMsg())){
            myNeighbours.next = new Address(request.getNewNeighbour());
        }
        if (request.getOwnAddress().equals(myNeighbours.prev.toAddressMsg())){
            myNeighbours.prev = new Address(request.getNewNeighbour());
        }
    }

    public void startElection() {
        delayHandler.handleRequestDelay("START_ELECTION");

//        electionService.tossElection(); <- starts erection
        electionService.tryElect();
        log.debug("Starting election");
    }

    public Address getPrevAddr() {
        return myNeighbours.prev.copy();
    }
    public Address getNextAddr() {
        return myNeighbours.next.copy();
    }
    public void setActivityStatus(boolean b) {
        terminationService.setPassive(!b);
    }
    public void detectTermination(){
        terminationService.initiateDetection();
    }

    public void selfDestruct() {
        log.info("Initiating self destruct protocol");
        log.info("System will be destroyed in 5 seconds");
        log.info("Shutting off the receivers");
        server.shutdown();
        int i = 5;
        while (i > 0){
            log.info("Self destruct in " + i + " seconds");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            i--;
        }
        System.exit(0);
    }

    public boolean isChannelDead(ManagedChannel channel) {
        NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
        try{
            stub.ping(Empty.newBuilder().build());
            return false;
        }catch (Exception e){
            log.debug("Given channel is dead");
            return true;
        }
    }

    public void prevBroken() {
        myNeighbours.prev = own.copy();
        chatService.topologyBroken(true);
    }

    public void nextBroken() {
        myNeighbours.next = own.copy();
        chatService.topologyBroken(false);
    }

    public void handlerSet(String command) {
        try {
            String[] split = command.split(" ");
            if (split.length != 3){
                log.error("Wrong set command");
                return;
            }
            delayHandler.set(split[1], split[2]);
        }catch (Exception e){
            log.error("Wrong set command");
        }
    }

    public void handleJoinCommand(String command) {
        if (myNeighbours.next.compareTo(own) != 0 || myNeighbours.prev.compareTo(own) != 0) {
            selfLogOut();
        }

        try {
            String[] split = command.split(" ");
            if (split.length != 3){
                log.error("Wrong join command");
                return;
            }
            Address address = new Address(split[1], Integer.parseInt(split[2]));
            tryJoin(address);
        } catch (Exception e) {
            log.error("Wrong join command");
        }
    }

    public void sendCalculationMsg(String command) {
        log.info("Sending calculation message to " + command);
        Address address;
        try {
            String[] split = command.split(" ");
            if (split.length != 3){
                log.error("Wrong calculation command");
                return;
            }

             address = new Address(split[1], Integer.parseInt(split[2]));
        } catch (Exception e) {
            log.error("Wrong calculation command");
            return;
        }
        terminationService.sendMessageTo(address);
        ManagedChannel channel = ManagedChannelBuilder.forAddress(address.hostname, address.port)
                .usePlaintext()
                .build();
        try {
            NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);
            stub.ping(Empty.newBuilder().build());
            closeChannelProperly(channel);
        }catch (Exception e){
            log.error("Node on " + address + " is not a part of the network or unreachable");
        }
    }

    public void LdrChangedTo(Address address) {
        leader = (address.compareTo(own)==0) ?
                new LocalLeader(this)
            :
                new RemoteLeader(this);

    }

    public void printAddressBook() {
        leader.updateAddressBook();
        log.info("Address book:");
        leader.getAddressBook()
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .forEach(entry -> log.info("    " + entry.getKey() + " : " + entry.getValue()));
    }

    public void msgSentTo(String recipient) {
        terminationService.msgSentTo(recipient);
    }

    public boolean isLeader() {
        return own.compareTo(myNeighbours.leader) == 0;
    }
}
