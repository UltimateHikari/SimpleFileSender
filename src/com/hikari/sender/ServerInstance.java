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
    private FileOutputStream file;
    private BufferedInputStream inputStream;
    private byte [] inputBuffer;
    private Metadata mdata = new Metadata();

    private boolean isReceiving = true;
    private final static int COUNTER_FREQUENCY = 1;


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

    public ServerInstance(Socket accept) {
        client = accept;
    }

    private void initResources() throws IOException {
        File locDir = new File(Metadata.LOCATION);
        locDir.mkdir();
        file = new FileOutputStream(mdata.getPath());
        inputBuffer = new byte [mdata.chunkByteSize()];
        inputStream = new BufferedInputStream(client.getInputStream());
        Runnable counter = () -> {
            counterCalls++;
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
        int cbs = mdata.chunkByteSize();
        return client.getLocalPort() + ": speed " +
                (delta*cbs + lastChunkSize)/COUNTER_FREQUENCY + " B/s, average " +
                (chunksCounted*cbs + lastChunkSize)/(COUNTER_FREQUENCY*counterCalls) ;
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
            mdata.fetchMetadata(client.getInputStream());
            log("Ready to receive to" + mdata.getPath() + " on " + client.getLocalPort());
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
        long savedSize = Files.size(Path.of(mdata.getPath()));
        if(mdata.verifyFileSize(savedSize)){
            client.getOutputStream().write("File saved".getBytes(StandardCharsets.UTF_8));
        } else {
            client.getOutputStream().write("Filesize mismatch".getBytes(StandardCharsets.UTF_8));
        }
    }
}
