package cz.cvut.fel.model;

import cz.cvut.fel.BrokenTopology;
import cz.cvut.fel.JoinResponse;
import cz.cvut.fel.model.Address;

import java.io.Serializable;

public class DSNeighbours implements Serializable {
    public Address next;
    public Address prev;
    public Address leader;

    public DSNeighbours (Address me) {
        this.next = me;
        this.prev = me;
        this.leader = me;
    }


    public DSNeighbours (Address next, Address prev, Address leader) {
        this.next = next;
        this.prev = prev;
        this.leader = leader;
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
    }

    public void setLostAsNeighbour(BrokenTopology msg) {
        Address lost = new Address(msg.getBrokenNode());
        if (msg.getIsPrevBroken()){
            this.next = lost;
        }else{
            this.prev = lost;
        }
    }
}
