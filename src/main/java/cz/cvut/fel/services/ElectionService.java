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

    public ElectionService(Node node){
        this.node = node;
        this.tid = node.getOwn();
        this.state = ElectionState.ACTIVE;
//        resetElection();
    }

    private void resetElection() {
        this.ntid = null;
        this.nntid = null;
        state = ElectionState.ACTIVE;
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
    @Override
    public void sendPID(AddressMsg request, StreamObserver<Empty> responseObserver) {
        log.debug("pid request" + new Address(request));
        NodeUtils.respondEmpty(responseObserver);
        handleHandshakes(request);
    }

    @Override
    public void tossCall(AddressPair request, StreamObserver<Empty> responseObserver) {
        NodeUtils.respondEmpty(responseObserver);
        switch (state){
            case ACTIVE:
                handleActive(request);
                break;
            case RELAY:
                handleRelay(request);
                break;
            case LEADER:
                break;
        }
    }

    private void handleActive(AddressPair request) {
        AddressPair addressPair = AddressPair.newBuilder().setNtid(ntid.toAddressMsg()).setNntid(nntid.toAddressMsg()).build();
        ntid = new Address(request.getNtid());
        nntid = new Address(request.getNntid());
        ElectionRound(addressPair);
    }

    public void handleHandshakes(AddressMsg request){
        Address neighborAddress = new Address(request);

        if (this.ntid == null) {
            this.ntid = neighborAddress;
            log.debug("Setting ntid to "+ neighborAddress);
            tossElection();
        } else if (this.nntid == null) {
            this.nntid = neighborAddress;
            log.debug("Setting nntid to "+ neighborAddress);
            tossElection();
        } else {
            ElectionRound(null);//
        }
    }

    public void ElectionRound(AddressPair addressPair){
        if (this.ntid.compareTo(tid) == 0){ // compare to
            this.state = ElectionState.LEADER;
            log.info("Switching state to leader");
            node.sendBroadcastMsg("I am the leader");
            AddressPair leadermsg = AddressPair.newBuilder()
                    .setNtid(node.getOwn().toAddressMsg())
                    .setNntid(node.getOwn().toAddressMsg())
                    .build();

            node.getMyNeighbours().setLeader(node.getOwn());
            host = null;
            informLeaderElected(leadermsg);

            // change inform reaction
            log.info("Leader has been elected. Node " + node.getUname() + " is the leader.");
            //TODO Finish election
        }
        if (this.ntid.compareTo(getMax()) == 0){ // compare to
            this.tid = ntid;
            log.info("Owning ntid");
        } else {
            this.state = ElectionState.RELAY;
            log.info("Switching state to relay");
        }
        if (addressPair == null){
            addressPair = AddressPair.newBuilder().setNtid(tid.toAddressMsg()).setNntid(ntid.toAddressMsg()).build();
        }

        tossPair(addressPair);
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
            log.info("Election started");
            tossElection();
        } // weaker host do nothing

    }

    private void informNext() {
        ManagedChannel channel = NodeUtils.openChannelToNext(node, true);
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

    private void tossPair(AddressPair addressPair) {
        ManagedChannel channel = openChannelToNext(node, true);
        ElectionServiceGrpc.ElectionServiceBlockingStub blockingStub = ElectionServiceGrpc.newBlockingStub(channel);
        blockingStub.tossCall(addressPair);

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
    public void handleRelay(AddressPair request){
        ManagedChannel channel = openChannelToNext(node, true);
        ElectionServiceGrpc.ElectionServiceBlockingStub blockingStub = ElectionServiceGrpc.newBlockingStub(channel);
//        blockingStub.sendPID(request);
        blockingStub.tossCall(request);
        Node.closeChannelProperly(channel);
    }


    private AddressMsg getIDtoToss(){
        return ((nntid==null)? tid:ntid).toAddressMsg();
    }

    public void tossElection(){
        ManagedChannel channel = openChannelToNext(node, true);
        ElectionServiceGrpc.ElectionServiceBlockingStub blockingStub = ElectionServiceGrpc.newBlockingStub(channel);
        blockingStub.sendPID(getIDtoToss());
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
        host = node.getOwn();
        resetElection();
        informNext();
    }


}
