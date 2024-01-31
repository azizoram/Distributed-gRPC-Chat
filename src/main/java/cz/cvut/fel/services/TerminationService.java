package cz.cvut.fel.services;

import cz.cvut.fel.*;
import cz.cvut.fel.leader.AbstractLdr;
import cz.cvut.fel.model.Address;
import cz.cvut.fel.utils.NodeUtils;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import static cz.cvut.fel.utils.NodeUtils.respondEmpty;


// A service responsible for handling termination based on Dijkstra, Feijen, Van Gasteren algorithm

@Slf4j(topic = "main_topic")
public class TerminationService extends TerminationServiceGrpc.TerminationServiceImplBase {
    @Getter
    private boolean isPassive = false;
    @Getter
    private boolean isBlack = false;

    private Token holdedToken = null;
    private boolean isInitiator = false;


    private Node node;

    public TerminationService(Node node) {
        this.node = node;
        isPassive = new Random().nextBoolean();
    }

    public void setPassive(boolean passive) {
        log.info("Going " + (passive ? "passive" : "active"));
        isPassive = passive;
        if (passive && holdedToken != null){
            processProcess(holdedToken);
        }
        if (isPassive && node.isLeader()){
            initiateDetection();
        }
    }

    public void initiateDetection() {
        log.info("Initiating termination detection");
        isInitiator = true;
        if (!isPassive){// if the node which initiated termination is not passive, it should act as if it received black token but have some buisness to do before making new white one
            log.info("Initiator is not passive");
            Token token = Token.newBuilder().setIsBlack(true).setHopCount(50).build();
            holdedToken = token;
            return;
        }
        Token token = Token.newBuilder().setIsBlack(false).setHopCount(50).build();

        pass(token);
    }

    private void pass(Token token) {
        ManagedChannel channel = getChannelToPrev();
        if (channel == null){
            log.error("Topology broken, cannot pass token");
            return;
        }
        TerminationServiceGrpc.TerminationServiceBlockingStub stub = TerminationServiceGrpc.newBlockingStub(channel);
        stub.detectTermination(token);
        Node.closeChannelProperly(channel);
    }

    private ManagedChannel getChannelToPrev() {
        AbstractLdr ldr = node.getLeader();
        ldr.updateAddressBook();
        Address prev = getPrev(node.getOwn(), ldr.getAddressBook());
        ManagedChannel channel = NodeUtils.openChannelTo(prev);
        while (node.isChannelDead(channel)){
            log.debug("Topology broken, prev is unresponsive, skipping");
            prev = getPrev(prev, ldr.getAddressBook());
            channel = NodeUtils.openChannelTo(prev);
        }
        return channel;
    }

    private Address getPrev(Address curr, Map<String, Address> addressBook) {
        Address[] addresses = addressBook.values().stream().sorted().toArray(Address[]::new);
        Address prev = addresses[addresses.length - 1];
        for (Address address : addresses) {
            if (address.compareTo(curr) < 0) {
                prev = address;
            }
        }
        return prev;
    }

    public void detectTermination(Token token, StreamObserver<Empty> responseObserver) {
        respondEmpty(responseObserver);
        if (!isPassive){
            log.info("Node is not passive, holding token");
            holdedToken = token;
            return;
        }

        processProcess(token);
    }

    private void processProcess(Token token) {
        Token hopken = token.toBuilder().setHopCount(token.getHopCount()-1).build();
        if (!isInitiator){
            handleIthProcess(hopken);
        }else {
            handleZeroth(hopken);
        }
    }

    private void handleZeroth(Token token) {
        if (token.getIsBlack()){
            Token newToken = Token.newBuilder().setIsBlack(false).setHopCount(50).build();
            pass(newToken);
            return;
        }
        node.sendBroadcastMsg("Termination detected!!");
    }

    private void handleIthProcess(Token token) {
        Token newToken = Token.newBuilder().setIsBlack(isBlack).setHopCount(token.getHopCount() - 1).build();
        pass(newToken);
        isBlack = false;
    }
    public void sendMessageTo(Address other){
        if (node.getOwn().compareTo(other) < 0){
            isBlack = true;
        }
    }

    public void askHash(Empty request, StreamObserver<StringMsg> responseObserver) {
        StringMsg msg = node.getLeader().getHashMsg();
        responseObserver.onNext(msg);
        responseObserver.onCompleted();
    }
    public void askAddressBook(Empty request, StreamObserver<AddressBookMsg> addressBookMsgStreamObserver){
        AddressBookMsg msg = node.getLeader().getAddressBookMsg();
        addressBookMsgStreamObserver.onNext(msg);
        addressBookMsgStreamObserver.onCompleted();
    }

    public void msgSentTo(String recipient) {
        List<Map.Entry<String, Address>> entryList = node.getLeader().getAddressBook()
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toList());
        for (Map.Entry<String, Address> stringAddressEntry : entryList) {
            if (stringAddressEntry.getKey().equals(recipient)) {
                sendMessageTo(stringAddressEntry.getValue());
                return;
            }
        }

    }
}
