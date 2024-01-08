package cz.cvut.fel.model;

import cz.cvut.fel.AddressMsg;

import java.io.Serializable;
import java.util.Arrays;

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


    private Integer[] barseHost(String host){
        return Arrays.stream(delocalhost(host).split("\\.")).map(Integer::parseInt).toArray(Integer[]::new);
    }

    @Override // a - b
    public int compareTo(Address ozer) {
        Integer[] own = barseHost(hostname);
        Integer[] other =  barseHost(ozer.hostname);
        for (int i = 0 ; i < 4; i++){
            if (!own[i].equals(other[i])){
                return own[i] - other[i];
            }
        }

        return port.compareTo(ozer.port);
    }

    public Address copy() {
        return new Address(this.hostname, this.port);
    }

    public AddressMsg toAddressMsg() {
        return AddressMsg.newBuilder().setIpAddress(hostname).setPort(port).build();
    }

    private String delocalhost(String host){
        return (host.equals("localhost"))?"127.0.0.1":host;
    }

}