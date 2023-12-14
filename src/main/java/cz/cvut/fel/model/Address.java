package cz.cvut.fel.model;

import cz.cvut.fel.AddressMsg;

import java.io.Serializable;

public class Address implements Comparable<Address>, Serializable {
    public String hostname;
    public Integer port;

    public Address (AddressMsg addressMsg) {
        this.hostname = addressMsg.getIpAddress();
        this.port = addressMsg.getPort();
    }

    public Address () {
        this("127.0.0.1", 2010);
    }


    public Address (String hostname, int port) {
        this.hostname = hostname;
        this.port = port;
    }


    public Address (Address addr) {
        this(addr.hostname, addr.port);
    }


    @Override
    public String toString() {
        return("Addr[host:'"+hostname+"', port:'"+port+"']");
    }


    @Override
    public int compareTo(Address address) {
        int retval = 0;
        if ((retval = hostname.compareTo(address.hostname)) == 0 ) {
            if ((retval = port.compareTo(address.port)) == 0 ) {
                return 0;
            }
            else
                return retval;
        }
        else
            return retval;
    }

    public Address copy() {
        return new Address(this.hostname, this.port);
    }
}