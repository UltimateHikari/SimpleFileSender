package com.hikari.sender;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.SECONDS;

public class Ticker {
    private int chunksReceived = 0;
    private int chunksCounted = 0;
    private int lastChunkSize = 0;
    private int counterCalls = 0;

    private final int chunkByteSize;
    private final String header;

    private Runnable canceller;

    public final static int COUNTER_FREQUENCY = 1;
    private boolean isReceiving = true;

    private final Runnable counter = () -> {
        counterCalls++;
        int delta = chunksReceived - chunksCounted;
        chunksCounted = chunksReceived;
        formatSpeed(delta);
        if(!isReceiving){
            canceller.run();
        }
    };

    public Ticker(int chunkByteSize, String header){
        this.chunkByteSize = chunkByteSize;
        this.header = header;
    }

    private void formatSpeed(int delta) {
        System.out.println(header + ": speed " +
                (delta*chunkByteSize + lastChunkSize)/COUNTER_FREQUENCY + " B/s, average " +
                (chunksCounted*chunkByteSize + lastChunkSize)/(COUNTER_FREQUENCY*counterCalls));
    }

    public void start(ScheduledExecutorService scheduler){
        ScheduledFuture<?> counterHandle =
                scheduler.scheduleAtFixedRate(counter, COUNTER_FREQUENCY, COUNTER_FREQUENCY, SECONDS);
        canceller = () -> counterHandle.cancel(false);
    }

    public void stopReceiving(int lastChunkSize){
        isReceiving = false;
        this.lastChunkSize = lastChunkSize;
    }

    public boolean isReceiving(){
        return isReceiving;
    }

    public void tickChunk() {
        chunksReceived++;
    }
}
