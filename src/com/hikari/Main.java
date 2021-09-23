package com.hikari;

import com.hikari.sender.SimpleClient;
import com.hikari.sender.SimpleServer;

import java.io.FileNotFoundException;
import java.io.IOException;

public class Main {

    public static void main(String[] args) {
        SimpleServer server = new SimpleServer(2, 3000);
        Thread thread = new Thread(server);
        thread.start();
        SimpleClient client = new SimpleClient();
        try {
            client.send("/home/andy/AndyData/githubtoken", "127.0.0.1", 3000);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
