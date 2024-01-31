package cz.cvut.fel.model;

import cz.cvut.fel.BrokenTopology;
import cz.cvut.fel.JoinResponse;
import cz.cvut.fel.Node;
import cz.cvut.fel.model.Address;

import java.io.Serializable;

public class DSNeighbours implements Serializable {
    public Address next;
    public Address prev;
    public Address leader;
    private final Node node;
    public DSNeighbours (Address me, Node node) {
        this.next = me;
        this.prev = me;
        this.leader = me;
        this.node = node;
    }


    public DSNeighbours (Address next, Address prev, Address leader, Node node) {
        this.next = next;
        this.prev = prev;
        this.leader = leader;
        this.node = node;
    }

    @Override
    public String toString() {
        return("Neigh[next:'"+next+"', " +
                "prev:'"+prev+"', " +
                "leader:'"+leader+"']");
    }

    public void set(JoinResponse response) {
        this.next = new Address(response.getNext());
        this.prev = new Address(response.getPrev());
        setLeader(new Address(response.getLeader()));
    }

    public void setLostAsNeighbour(BrokenTopology msg) {
        Address lost = new Address(msg.getBrokenNode());
        if (msg.getIsPrevBroken()){
            this.next = lost;
        }else{
            this.prev = lost;
        }
    }

    public void setLeader(Address address) {
        boolean ldrChngd = (address.compareTo(this.leader)!=0);
        this.leader = address.copy();
        if (ldrChngd) node.LdrChangedTo(address);
    }
}
