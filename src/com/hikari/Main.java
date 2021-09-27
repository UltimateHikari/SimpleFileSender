package com.hikari;

import com.hikari.sender.SimpleClient;
import com.hikari.sender.SimpleServer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

public class Main {

    private static final String HELP_MSG = "filesend OPTION PORT [HOSTNAME] [PATH] \n" +
            "Where OPTION := {c | s};\n" +
            "latter args needed for c as client;\n" +
            "args order matters.";

    public static void main(String[] args){
        if(args.length == 0){
            System.out.println(HELP_MSG);
            return;
        }
        if(args[0].equals("c") && args.length > 3){
            SimpleClient client = new SimpleClient();
            String path = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            System.out.println("launching client for " + path);
            try {
                client.send(path, args[2], Integer.valueOf(args[1]));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if(args[0].equals("s") && args.length == 2){
            SimpleServer server = new SimpleServer(2, Integer.valueOf(args[1]));
            server.run();
        }
    }
}
