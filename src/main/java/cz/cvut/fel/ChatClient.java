package cz.cvut.fel;

import java.io.BufferedReader;
import java.io.IOException;

import static java.lang.System.err;

public class ChatClient implements Runnable{
    private boolean running = true;
    private BufferedReader reader = null;
    private Node node;
    public ChatClient(Node node){
        this.node = node;
        reader =new BufferedReader(new java.io.InputStreamReader(System.in));
    }

    private void parse_commandline(String commandline) {
        if (commandline.equals("h")) {
            node.sendHelloToNext();
        } else if (commandline.equals("s")) {
            node.printStatus();
        } else if (commandline.equals("?")) {
            System.out.print("? - this help");
            System.out.print("h - send Hello message to Next neighbour");
            System.out.print("s - print node status");
        } else {
            node.sendBroadcastMsg(commandline);
//            ChatMessage message = ChatMessage.newBuilder().setName(node.getUname()).setMessage(commandline).build();
//            node.sendMessage(message, null);
        }
    }

    @Override
    public void run() {
        String commandline;

        while (running){
            commandline = "";
            System.out.print("\ncmd > ");

//            node.printStatus();
            try {
                commandline = reader.readLine();
                parse_commandline(commandline);
            } catch (IOException e) {
                err.println("Error in reading console input.");
                e.printStackTrace();
                running = false;
            }
        }
        System.out.println("Closing chat client.");
    }

    public void reciveBcastMsg(BroadcastMessage msg) {
        System.out.println("B!" + msg.getAuthor() + " : " + msg.getMessage());
    }
}
