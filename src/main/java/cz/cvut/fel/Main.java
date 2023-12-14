package cz.cvut.fel;

import cz.cvut.fel.model.Address;

public class Main {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java -jar <jar-file> <uname> <port>");
            System.exit(1);
        }
        Node node = new Node(args[0], new Address("localhost", Integer.parseInt(args[1])));
        node.run();
    }
}