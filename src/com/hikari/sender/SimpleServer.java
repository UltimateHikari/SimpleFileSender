package com.hikari.sender;

import java.io.IOException;
import java.net.ServerSocket;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimpleServer implements Runnable{
    private final int nThreads;
    private final int port;

    private void log(String s){
        System.out.println(s);
    }

    public SimpleServer(int nThreads, int port){
        this.nThreads = nThreads;
        this.port = port;
    }

    @Override
    public void run() {
        ServerSocket serverSocket;
        ExecutorService pool = Executors.newFixedThreadPool(nThreads);
        try{
            serverSocket = new ServerSocket(port);
            log("SimpleFileServer started on port " + port);
            while(true){
                ServerInstance serverInstance = new ServerInstance(serverSocket.accept());
                log("New client connected");
                pool.execute(serverInstance);
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }
}
