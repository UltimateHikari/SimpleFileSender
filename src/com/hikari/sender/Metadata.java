package com.hikari.sender;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class Metadata {
    public final static int SERVICE_DATA_LEN = 13;
    public final static int SERVICE_CHUNK_LEN = 4;
    public final static String LOCATION = "./uploads";
    private String filename;
    private long fileSize;
    private byte chunkSize;
    private int filenameLength;

    private final static int KB = 1024;

    public Metadata(String filename, long fileSize, byte chunkSize){
        this.filename = filename;
        this.fileSize = fileSize;
        this.chunkSize = chunkSize;
    }

    public Metadata(String filename, long fileSize){
        this(filename, fileSize, (byte)100);
    }

    public Metadata(){
        this("",0);
    }

    public int chunkByteSize(){
        //obvious upper limit is 255kb per chunk
        return chunkSize*KB;
    }

    public void sendMetadata(BufferedOutputStream outputStream) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(SERVICE_DATA_LEN);
        byte [] name = filename.getBytes(StandardCharsets.UTF_8);

        byteBuffer.putInt(name.length);
        byteBuffer.putLong(fileSize);
        byteBuffer.put(chunkSize);

        outputStream.write(byteBuffer.array());
        outputStream.write(name);
    }

    private void verifyMetadataRead(InputStream stream, byte [] buf, int expected) throws IOException {
        if (stream.read(buf) < expected){
            throw new IOException("Corrupted metadata, can't initiate");
        }
    }

    private void fetchServiceInfo(InputStream stream) throws IOException {
        byte [] buf = new byte[SERVICE_DATA_LEN];
        verifyMetadataRead(stream, buf, SERVICE_DATA_LEN);
        ByteBuffer byteBuffer = ByteBuffer.wrap(buf);
        filenameLength = byteBuffer.getInt();
        fileSize = byteBuffer.getLong();
        chunkSize = byteBuffer.get();
    }

    public void fetchMetadata(InputStream stream) throws IOException {
        fetchServiceInfo(stream);
        byte [] buf = new byte[filenameLength];
        verifyMetadataRead(stream, buf, filenameLength);
        filename = new String(buf, StandardCharsets.UTF_8);
    }

    public String getPath(){
        return LOCATION + "/" + filename;
    }

    public boolean verifyFileSize(long size){
        System.out.println("Metadata: verifying received" + size + " against " + fileSize);
        return fileSize == size;
    }
}
