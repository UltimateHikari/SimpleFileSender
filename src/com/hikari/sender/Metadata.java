package com.hikari.sender;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class Metadata {
    public final static int SERVICE_DATA_LEN = 13;
    public final static int SERVICE_CHUNK_LEN = 4;
    public final static String LOCATION = "./uploads";
    private String filename;
    private long fileSize;
    private byte chunkSize;
    private int filenameLength;
    private MessageDigest messageDigest = MessageDigest.getInstance("MD5");

    private final static int KB = 1024;
    private byte[] receivedHash = new byte[16];

    public Metadata(String filename, long fileSize, byte chunkSize) throws NoSuchAlgorithmException {
        this.filename = filename;
        this.fileSize = fileSize;
        this.chunkSize = chunkSize;
    }

    public Metadata(String filename, long fileSize) throws NoSuchAlgorithmException {
        this(filename, fileSize, (byte)100);
    }

    public Metadata() throws NoSuchAlgorithmException {
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
        if (stream.read(buf, 0, expected) < expected){
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

    public boolean verifyMD5(){
        byte [] calculatedHash = messageDigest.digest();
        System.out.println("Metadata: verifying received "
                + new String(receivedHash)
                + " against "
                + new String(calculatedHash));
        return Arrays.equals(calculatedHash, receivedHash);
    }

    public void sendHash(BufferedOutputStream outputStream) throws IOException {
        byte[] bytes = messageDigest.digest();
        outputStream.write(
                ByteBuffer.allocate(4).putInt(bytes.length).array(), 0, 4
        );
        outputStream.write(bytes);
        outputStream.flush();
    }

    public void updateDigest(byte[] buf, int actualBytesRead) {
        messageDigest.update(buf, 0, actualBytesRead);
    }

    public void fetchHash(InputStream stream) throws IOException {
        System.out.println("fetching hash...");
        byte [] bsize = new byte[4];
        verifyMetadataRead(stream, bsize, 4);
        int size = ByteBuffer.wrap(bsize).getInt();
        receivedHash = new byte[size];
        verifyMetadataRead(stream, receivedHash, size);
    }

}
