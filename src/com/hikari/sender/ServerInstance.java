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
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.SECONDS;

public class ServerInstance implements Runnable {
    private final Socket client;
    private boolean isReceiving = true;

    private final static int SERVICE_DATA_LEN = 13;
    private final static int SERVICE_CHUNK_LEN = 4;
    private final static int COUNTER_FREQUENCY = 1;
    private final static int KB = 1024;
    private final static String location = "./uploads";

    private byte chunkSize = 100;
    private int filenameLength;
    private String filename;
    private long fileSize;

    private FileOutputStream file;
    private BufferedInputStream inputStream;
    private byte [] inputBuffer;
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);
    private Runnable canceller;

    private int chunksReceived = 0;
    private int chunksCounted = 0;
    private int lastChunkSize = 0;
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
        fileSize = byteBuffer.getLong();
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
            counterCalls++; //TODO print even on fast files
            int delta = chunksReceived - chunksCounted;
            chunksCounted = chunksReceived;
            log(formatSpeed(delta));
            if(!isReceiving){
                canceller.run();
            }
        };
        ScheduledFuture<?> counterHandle =
                scheduler.scheduleAtFixedRate(counter, COUNTER_FREQUENCY, COUNTER_FREQUENCY, SECONDS);
        canceller = () -> counterHandle.cancel(false);

    }

    private String formatSpeed(int delta) {
        return client.getLocalPort() + ": speed " +
                (delta*chunkByteSize() + lastChunkSize)/COUNTER_FREQUENCY + " B/s, average " +
                (chunksCounted*chunkByteSize() + lastChunkSize)/(COUNTER_FREQUENCY*counterCalls) + "\n";
    }

    private void freeResources(){
        try {
            log("closing");
            //scheduler.execute(canceller); // lazy solution
            file.close();
            inputStream.close();
            client.close();
            scheduler.awaitTermination(COUNTER_FREQUENCY, SECONDS);
            scheduler.shutdown();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private int receiveChunk() throws IOException {
        byte [] buf = new byte[SERVICE_CHUNK_LEN];
        if(inputStream.read(buf) < SERVICE_CHUNK_LEN){
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

        if(actualChunkBytes < chunkByteSize()){
            isReceiving = false;
            lastChunkSize = actualChunkBytes;
        } else {
            chunksReceived++;
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

    private void verifyReceivedFile() throws IOException {
        long savedSize = Files.size(Path.of(location + "/" + filename));
        log("verifying " + savedSize + " against " + fileSize);
        if(savedSize == fileSize){
            client.getOutputStream().write("File saved".getBytes(StandardCharsets.UTF_8));
        } else {
            client.getOutputStream().write("Filesize mismatch".getBytes(StandardCharsets.UTF_8));
        }
    }
}
