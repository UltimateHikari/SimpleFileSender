package com.hikari.sender;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SimpleClient {
    private String filename;
    private long fileSize;

    private Socket client;
    private FileInputStream file;
    private BufferedOutputStream outputStream;
    private byte [] outputBuffer;

    private final byte chunkSize = 100;
    private final static int SERVICE_DATA_LEN = 13;
    private final static int SERVICE_CHUNK_LEN = 4;
    private boolean isSending = true;
    private final static int KB = 1024;

    private void log(String s){
        System.out.println("KLUEND: " + s);
    }


    private int chunkByteSize(){
        //obvious upper limit is 255kb per chunk
        return chunkSize*KB;
    }

    private void initResources(String path, String hostname, Integer port) throws IOException {
        File filePath = new File(path);
        filename = filePath.getName();
        fileSize = Files.size(Path.of(path));
        file = new FileInputStream(path);

        client = new Socket();
        client.connect(new InetSocketAddress(hostname, port));

        outputBuffer = new byte [chunkByteSize()];
        outputStream = new BufferedOutputStream(client.getOutputStream());
    }

    private void sendMetadata() throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(SERVICE_DATA_LEN);
        byte [] bytename = filename.getBytes(StandardCharsets.UTF_8);
        byteBuffer.putInt(bytename.length);
        byteBuffer.putLong(fileSize);
        byteBuffer.put(chunkSize);
        outputStream.write(byteBuffer.array());
        outputStream.write(bytename);
    }

    private int readChunk() throws IOException {
        return file.read(outputBuffer);
    }
    private void sendChunk(int actualBytesRead) throws IOException{
        byte [] buf = ByteBuffer.allocate(SERVICE_CHUNK_LEN).putInt(actualBytesRead).array();
        outputStream.write(buf);
        outputStream.write(outputBuffer, 0, actualBytesRead);
        if(actualBytesRead < chunkByteSize()){
            isSending = false;
        }
    }

    public void send(String path, String hostname, Integer port) throws IOException{
        initResources(path, hostname, port);
        sendMetadata();
        while(isSending){
            sendChunk(readChunk());
        }
        getTransferStatus();
        freeResources();
    }

    private void getTransferStatus() throws IOException {
        outputStream.flush();
        String status = new String(
                client.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        log(status);
    }

    private void freeResources() throws IOException {
        file.close();
        outputStream.close();
        client.close();
    }

}
