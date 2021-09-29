package com.hikari.sender;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.TimeUnit.SECONDS;

public class ServerInstance implements Runnable {
    private final Socket client;
    private FileOutputStream file;
    private BufferedInputStream inputStream;
    private byte [] inputBuffer;
    private final Metadata mdata = new Metadata();
    private Ticker ticker;

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);

    private void log(String s){
        System.out.println("SWEWER: " + s);
    }

    public ServerInstance(Socket accept) {
        client = accept;
    }

    private void initResources() throws IOException {
        File locDir = new File(Metadata.LOCATION);
        locDir.mkdir();
        file = new FileOutputStream(mdata.getPath());
        inputBuffer = new byte [mdata.chunkByteSize()];
        inputStream = new BufferedInputStream(client.getInputStream());
        ticker = new Ticker(mdata.chunkByteSize(), "SWEWER on port " + client.getLocalPort());
        ticker.start(scheduler);
    }

    private void freeResources(){
        try {
            log("closing");
            //scheduler.execute(canceller); // lazy solution
            file.close();
            inputStream.close();
            client.close();
            scheduler.awaitTermination(Ticker.COUNTER_FREQUENCY, SECONDS);
            scheduler.shutdown();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private int receiveChunk() throws IOException {
        byte [] buf = new byte[Metadata.SERVICE_CHUNK_LEN];
        if(inputStream.read(buf) < Metadata.SERVICE_CHUNK_LEN){
            throw new IOException("got corrupted chunksize");
        }
        ByteBuffer byteBuffer = ByteBuffer.wrap(buf);
        int actualChunkBytes = byteBuffer.getInt();
        int receivedBytes = 0;
        while(receivedBytes < actualChunkBytes){
            int readRes = inputStream.read(inputBuffer,
                    receivedBytes,
                    inputBuffer.length - receivedBytes);
            if(readRes == -1){
                throw new IOException("End of stream on read: " + receivedBytes + " < " + actualChunkBytes);
            }
            receivedBytes += readRes;
        }

        if(actualChunkBytes < mdata.chunkByteSize()){
            ticker.stopReceiving(actualChunkBytes);
        } else {
            ticker.tickChunk();
        }
        return actualChunkBytes;
    }

    private void writeChunk(int actualChunkBytes) throws IOException {
        file.write(inputBuffer, 0, actualChunkBytes);
    }

    @Override
    public void run() {
        try {
            mdata.fetchMetadata(client.getInputStream());
            log("Ready to receive to" + mdata.getPath() + " on " + client.getLocalPort());
            initResources();
            while (ticker.isReceiving()) {
                writeChunk(receiveChunk());
            }
            verifyReceivedFile();
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            freeResources();
        }
    }

    private void verifyReceivedFile() throws IOException {
        long savedSize = Files.size(Path.of(mdata.getPath()));
        if(mdata.verifyFileSize(savedSize)){
            client.getOutputStream().write("File saved".getBytes(StandardCharsets.UTF_8));
        } else {
            client.getOutputStream().write("Filesize mismatch".getBytes(StandardCharsets.UTF_8));
        }
    }
}
