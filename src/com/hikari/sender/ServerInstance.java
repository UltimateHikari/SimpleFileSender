package com.hikari.sender;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.SECONDS;

public class ServerInstance implements Runnable {
    private final Socket client;
    private boolean isReceiving = true;

    private final static int SERVICE_DATA_LEN = 5;
    private final static int SERVICE_CHUNK_LEN = 4;
    private final static int COUNTER_FREQUENCY = 5;
    private final static int KB = 1024;
    private final static String location = "./uploads";

    private byte chunkSize = 100;
    private int filenameLength;
    private String filename;

    private FileOutputStream file;
    private BufferedInputStream inputStream;
    private byte [] inputBuffer;
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);
    private Runnable canceller;

    private int chunksReceived = 0;
    private int chunksCounted = 0;
    private int counterCalls = 0;

    private void log(String s){
        System.out.println("SWEWER: " + s);
    }

    private int chunkByteSize(){
        //obvious upper limit is 255kb per chunk
        return chunkSize*KB;
    }

    public ServerInstance(Socket accept) {
        client = accept;
    }

    private void verifyMetadataRead(byte [] buf, int expected) throws IOException {
        if (client.getInputStream().read(buf) < expected){
            throw new IOException("Corrupted metadata, can't initiate");
        }
    }

    private void fetchServiceInfo() throws IOException {
        byte [] buf = new byte[SERVICE_DATA_LEN];
        verifyMetadataRead(buf, SERVICE_DATA_LEN);
        ByteBuffer byteBuffer = ByteBuffer.wrap(buf);
        filenameLength = byteBuffer.getInt();
        chunkSize = byteBuffer.get();
    }

    private void fetchMetadata() throws IOException {
        fetchServiceInfo();
        byte [] buf = new byte[filenameLength];
        verifyMetadataRead(buf, filenameLength);
        filename = new String(buf, StandardCharsets.UTF_8);
        log("Ready to receive " + filename + " on " + client.getLocalPort());
    }

    private void initResources() throws IOException {
        File locDir = new File(location);
        locDir.mkdir();
        file = new FileOutputStream(location + "/"+ filename);
        inputBuffer = new byte [chunkByteSize()];
        inputStream = new BufferedInputStream(client.getInputStream());
        Runnable counter = () -> {
            counterCalls++;
            int delta = chunksReceived - chunksCounted;
            chunksCounted = chunksReceived;
            log(formatSpeed(delta));
        };
        ScheduledFuture<?> counterHandle =
                scheduler.scheduleAtFixedRate(counter, COUNTER_FREQUENCY, COUNTER_FREQUENCY, SECONDS);
        canceller = () -> counterHandle.cancel(false);

    }

    private String formatSpeed(int delta) {
        return client.getLocalPort() + ": speed " +
                delta*chunkByteSize()/COUNTER_FREQUENCY + " B/s, average " +
                chunksCounted*chunkByteSize()/(COUNTER_FREQUENCY*counterCalls) + "\n";
    }

    private void freeResources(){
        try {
            scheduler.execute(canceller); // lazy solution
            file.close();
            inputStream.close();
            client.close();
            scheduler.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int receiveChunk() throws IOException {
        byte [] buf = new byte[SERVICE_CHUNK_LEN];
        verifyMetadataRead(buf, SERVICE_CHUNK_LEN);
        ByteBuffer byteBuffer = ByteBuffer.wrap(buf);
        int actualChunkBytes = byteBuffer.getInt();
        if(inputStream.read(inputBuffer) < actualChunkBytes){
            throw new IOException("read less than " + actualChunkBytes);
        }
        chunksReceived++;
        if(actualChunkBytes < chunkByteSize()){
            isReceiving = false;
        }
        return actualChunkBytes;
    }

    private void writeChunk(int actualChunkBytes) throws IOException {
        file.write(inputBuffer, 0, actualChunkBytes);
    }

    @Override
    public void run() {
        try {
            fetchMetadata();
            initResources();
            while (isReceiving) {
                writeChunk(receiveChunk());
            }
            verifyReceivedFile();
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            freeResources();
        }
    }

    private void verifyReceivedFile() {
        log("file received");//TODO
    }
}
