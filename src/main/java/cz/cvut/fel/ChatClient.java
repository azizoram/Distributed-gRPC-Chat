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
        } else if (commandline.startsWith("/calculationMsg")){
            node.sendCalculationMsg(commandline);
        } else if (commandline.startsWith("/who")){
            node.printAddressBook();
        }
        else if (commandline.startsWith("/logout")) {
            node.selfLogOut();
        } else if(commandline.startsWith("/selfDestruct")){
            node.selfDestruct();
        }
        else if (commandline.startsWith("/status")) {
            node.printStatus();
        } else if (commandline.startsWith("/help")) {
            System.out.println("/help - this help");
            System.out.println("/status - print node status");
            System.out.println("/dm <username> <message> - send direct message to user");
            System.out.println("/elect - start election");
            System.out.println("/td - detect termination");
            System.out.println("/setActive - set node active for termination detection");
            System.out.println("/setPassive - set node passive for termination detection");
            System.out.println("/set date HH:mm - set election delay until HH:mm");
            System.out.println("/join <ip> <port> - join to the specific node");
            System.out.println("/logout - logout from the network");
            System.out.println("/selfDestruct - destroy the node without informing the network");
            System.out.println("/who - print address book");
            System.out.println("/calculationMsg <ip> <port> <message> - send calculation message directly to the node");


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

    public void receiveMsg(Message msg){
        System.out.println((msg.getIsBcast()?"B! ":"D! ") + msg.getAuthor() + " : " + msg.getMessage());
    }

    public void failedDirectMsg(DirectMessage message) {
        System.out.println("User named :" + message.getRecipient() + " - not found!!!");
    }
}
