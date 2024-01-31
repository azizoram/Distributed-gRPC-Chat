package cz.cvut.fel;

import cz.cvut.fel.model.Address;

public class Main {
    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java -jar <jar-file> <uname> <own_ip> <port>");
            System.exit(1);
        }
        Node node = new Node(args[0], new Address(args[1], Integer.parseInt(args[2])));
        node.run();
    }
}