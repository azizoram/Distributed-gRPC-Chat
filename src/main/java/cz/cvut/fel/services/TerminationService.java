package cz.cvut.fel.services;

import cz.cvut.fel.Empty;
import cz.cvut.fel.Node;
import cz.cvut.fel.TerminationServiceGrpc;
import cz.cvut.fel.Token;
import cz.cvut.fel.model.Address;
import cz.cvut.fel.utils.NodeUtils;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;


// A service responsible for handling termination based on Dijkstra, Feijen, Van Gasteren algorithm

@Slf4j(topic = "bimbam")
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
    }

    public void initiateDetection() {
        log.info("Initiating termination");
        isInitiator = true;
        if (!isPassive){// if the node which initiated termination is not passive, it should act as if it recived black token but have some buisness to do before making new white one
            log.info("Initiator is not passive");
            Token token = Token.newBuilder().setIsBlack(true).build();
            holdedToken = token;
            return;
        }
        Token token = Token.newBuilder().setIsBlack(false).build();

        pass(token);
    }

    private void pass(Token token) {
        ManagedChannel channel = NodeUtils.openChannelToPrev(node);
        TerminationServiceGrpc.TerminationServiceBlockingStub stub = TerminationServiceGrpc.newBlockingStub(channel);
        stub.detectTermination(token);
        Node.closeChannelProperly(channel);
    }

    public void detectTermination(Token token, StreamObserver<Empty> responseObserver) {
        responseObserver.onNext(Empty.newBuilder().build());
        if (!isPassive){
            log.info("Node is not passive, holding token");
            holdedToken = token;
            return;
        }

        processProcess(token);
    }

    private void processProcess(Token token) {
        if (!isInitiator){
            handleIthProcess(token);
        }else {
            handleZeroth(token);
        }
    }

    private void handleZeroth(Token token) {
        if (token.getIsBlack()){
            Token newToken = Token.newBuilder().setIsBlack(false).build();
            pass(newToken);
            return;
        }
        log.info("Termination detected!!");
        node.sendBroadcastMsg("Termination detected!!");
    }

    private void handleIthProcess(Token token) {
        Token newToken = Token.newBuilder().setIsBlack(isBlack).build();
        pass(newToken);
        isBlack = false;
    }
    private void sendMessageTo(Address other){
        if (node.getOwn().compareTo(other) < 0){
            isBlack = true;
        }
    }
}
