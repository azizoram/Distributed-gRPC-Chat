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
        if (commandline.isEmpty()) return;
        if (commandline.startsWith("/dm")) {
            node.sendDirectMsg(commandline);
        } else if (commandline.startsWith("/elect")) {
            node.startElection();
        } else if (commandline.startsWith("/td")) {
            node.detectTermination();
        } else if (commandline.startsWith("/setActive")) {
            node.setActivityStatus(true);
        } else if (commandline.startsWith("/setPassive")) {
            node.setActivityStatus(false);
        } else if (commandline.startsWith("/set")){
            node.handlerSet(commandline);
        } else if (commandline.startsWith("/join")){
            node.handleJoinCommand(commandline);
        }
        else if (commandline.startsWith("/logout")) {
            node.selfLogOut();
        } else if(commandline.startsWith("/selfDestruct")){
            node.selfDestruct();
        }
        else if (commandline.equals("s")) {
            node.printStatus();
        } else if (commandline.startsWith("/help")) {
            System.out.print("/help - this help");
            System.out.print("s - print node status");
        } else {
            node.sendBroadcastMsg(commandline);
        }
    }

    @Override
    public void run() {
        String commandline;

        while (running){
            commandline = "";
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

    public void receiveDirectMsg(DirectMessage message) {
        System.out.println("D!" + message.getAuthor() + " : " + message.getMessage());
    }

    public void failedDirectMsg(DirectMessage message) {
        System.out.println("User named :" + message.getRecipient() + " - not found!!!");
    }
}
