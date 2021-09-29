package com.hikari.sender;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SimpleClient {
    private Socket client;
    private FileInputStream file;
    private BufferedOutputStream outputStream;
    private byte [] outputBuffer;
    private Metadata mdata;

    private boolean isSending = true;

    private void log(String s){
        System.out.println("KLUEND: " + s);
    }

    private void initResources(String path, String hostname, Integer port) throws IOException {
        File filePath = new File(path);
        mdata = new Metadata(filePath.getName(), Files.size(Path.of(path)));

        file = new FileInputStream(path);

        client = new Socket();
        client.connect(new InetSocketAddress(hostname, port));

        outputBuffer = new byte [mdata.chunkByteSize()];
        outputStream = new BufferedOutputStream(client.getOutputStream());
    }

    private int readChunk() throws IOException {
        return file.read(outputBuffer);
    }
    private void sendChunk(int actualBytesRead) throws IOException{
        byte [] buf = ByteBuffer.allocate(Metadata.SERVICE_CHUNK_LEN).putInt(actualBytesRead).array();
        outputStream.write(buf);
        log(" " + actualBytesRead);
        if(actualBytesRead < mdata.chunkByteSize()){
            isSending = false;
        }
        outputStream.write(outputBuffer, 0, actualBytesRead);
    }

    public void send(String path, String hostname, Integer port) throws IOException{
        initResources(path, hostname, port);
        mdata.sendMetadata(outputStream);
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
