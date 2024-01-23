package cz.cvut.fel.services;

import cz.cvut.fel.*;
import cz.cvut.fel.model.Address;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import cz.cvut.fel.utils.NodeUtils;

import lombok.extern.slf4j.Slf4j;

import static cz.cvut.fel.utils.NodeUtils.openChannelToNext;
import static cz.cvut.fel.utils.NodeUtils.respondEmpty;

@Slf4j(topic = "main_topic")
public class ElectionService extends ElectionServiceGrpc.ElectionServiceImplBase{
    private Node node;
    private Address tid;
    private Address ntid;
    private Address nntid;
    private ElectionState state;
    private Address host = null;
    private final Address sneaky;

    public ElectionService(Node node){
        this.node = node;
        this.tid = node.getOwn();
        this.state = ElectionState.ACTIVE;
//        resetElection();

        this.sneaky = new Address("0.0.0.0",
                (int) (Math.random() * Integer.MAX_VALUE)
                ); // make port different for different nodes
    }

    private void resetElection() {
        this.ntid = null;
        this.nntid = null;
        state = ElectionState.ACTIVE;
    }
    public void resetState(){
        log.debug("Node election state has been reset due to broken topology");
        resetElection();
        host = null;
    }

//    @Override
    public void sendLeader(AddressPair leaderMsg, StreamObserver<Empty> responseObserver) {
        NodeUtils.respondEmpty(responseObserver);

        host = null;
        if (new Address(leaderMsg.getNntid()).compareTo(node.getOwn()) == 0){
            return;
        }
        log.info("Leader now is " + new Address(leaderMsg.getNtid()));
        node.getMyNeighbours().setLeader(new Address(leaderMsg.getNtid()));
        node.notifyLeader();
        ManagedChannel channel = openChannelToNext(node, true);
        ElectionServiceGrpc.ElectionServiceBlockingStub blockingStub = ElectionServiceGrpc.newBlockingStub(channel);
        blockingStub.sendLeader(leaderMsg);
        Node.closeChannelProperly(channel);
    }

    public void passNTID(AddressMsg request, StreamObserver<Empty> responseObserver){
        NodeUtils.respondEmpty(responseObserver);
        ManagedChannel channel = openChannelToNext(node, true);
        ElectionServiceGrpc.ElectionServiceBlockingStub stub = ElectionServiceGrpc.newBlockingStub(channel);
        nntid = null;
        if (state == ElectionState.RELAY) {
            stub.passNTID(request);
        }else if (ntid != null){
            stub.passNNTID(ntid.toAddressMsg());
        }else {
            ntid = new Address(request);
            stub.passNTID(tid.toAddressMsg());
        }
        Node.closeChannelProperly(channel);
    }

    @Override
    public void passNNTID(AddressMsg request, StreamObserver<Empty> responseObserver) {
        NodeUtils.respondEmpty(responseObserver);
        ManagedChannel channel = openChannelToNext(node, true);
        ElectionServiceGrpc.ElectionServiceBlockingStub stub = ElectionServiceGrpc.newBlockingStub(channel);

        if(state==ElectionState.RELAY){
            stub.passNNTID(request);
        }else if (nntid != null){
            stub.passNTID(tid.toAddressMsg());
        }else{
            nntid = new Address(request);
            AddressMsg nntidmsg = ntid.toAddressMsg();
            if(handleGeorgeKlic()){
                stub.passNNTID(nntidmsg);
            }
        }
        Node.closeChannelProperly(channel);
    }

    // handle george klic
    // node has actual ntid and nntid pair and can do bingo byngo bongo
    private boolean handleGeorgeKlic(){
        log.debug("Handling election round \nTid: {} \nNtid: {} \nNNtid: {}", tid, ntid, nntid);
        if (this.ntid.compareTo(tid) == 0){
            becameLeader();
            return false;
        }

        if (ntid.compareTo(getMax()) == 0){
            tid = ntid;
        }else {
            becameRelay();
        }
        ntid=null;
        return true;
    }

    private void becameRelay() {
        this.state = ElectionState.RELAY;
        log.info("Switching state to relay");
    }

    private void becameLeader() {
        this.state = ElectionState.LEADER;
        this.node.setActivityStatus(true);
        log.info("Switching state to leader");
        AddressPair leadermsg = AddressPair.newBuilder()
                .setNtid(node.getOwn().toAddressMsg())
                .setNntid(node.getOwn().toAddressMsg())
                .build();

        node.getMyNeighbours().setLeader(node.getOwn());
        host = null;
        informLeaderElected(leadermsg);
    }

    public void informElection(AddressMsg message, StreamObserver<Empty> responseObserver){
        respondEmpty(responseObserver);
        Address electionHost = new Address(message);

        resetElection();

        if ((host == null || electionHost.compareTo(host) > 0)){ // stronger host nor no host
            host = electionHost;
            informNext();
        } else if (electionHost.compareTo(host) == 0){ // circle ended
            // Election started
            if (electionHost.compareTo(sneaky) == 0) {
                host = null;
                return;
            }

            log.info("Election started");
            startElection();
        } // weaker host do nothing

    }

    private void informNext() {
        ManagedChannel channel = NodeUtils.openChannelToNext(node, true);
        if (channel == null){
            return;
        }
        ElectionServiceGrpc.ElectionServiceBlockingStub stub = ElectionServiceGrpc.newBlockingStub(channel);
        stub.informElection(host.toAddressMsg());
        Node.closeChannelProperly(channel);
    }

    private void informLeaderElected(AddressPair leadermsg) {
        ManagedChannel channel = openChannelToNext(node, true);
        ElectionServiceGrpc.ElectionServiceBlockingStub blockingStub = ElectionServiceGrpc.newBlockingStub(channel);
        blockingStub.sendLeader(leadermsg);
        Node.closeChannelProperly(channel);
    }

    // cmp ret a > b: 1, a == b: 0, a < b: -1
    public Address getMax(){
        Address maximum = this.tid;
        if (this.ntid.compareTo(maximum) == 1){
            maximum = this.ntid;
        }
        if (this.nntid.compareTo(maximum) == 1){
            maximum = this.nntid;
        }
        return maximum;
    }

    public void startElection(){
        ManagedChannel channel = openChannelToNext(node, true);
        ElectionServiceGrpc.ElectionServiceBlockingStub blockingStub = ElectionServiceGrpc.newBlockingStub(channel);
        blockingStub.passNTID(this.tid.toAddressMsg());
        Node.closeChannelProperly(channel);
    }

    public String getState() {
        return state.toString();
    }

    public void tryElect() {
        if (host != null){
            log.info("There is already an election going on, by host: {}", host);
            return;
        }

        sneakyPreheat();

        host = node.getOwn();
        resetElection();
        informNext();
    }

    private void sneakyPreheat(){
        host = sneaky;
        informNext();

        NodeUtils.holdThreadFor(950, ignored->this.host != null);


        host = null;
    }


}
