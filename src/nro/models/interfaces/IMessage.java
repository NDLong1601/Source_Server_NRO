package nro.models.interfaces;

import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public interface IMessage {
    public int read() throws IOException;

    public int read(byte[] var1) throws IOException;

    public int read(byte[] var1, int var2, int var3) throws IOException;

    public boolean readBoolean() throws IOException;

    public byte readByte() throws IOException;

    public short readShort() throws IOException;

    public int readInt() throws IOException;

    public long readLong() throws IOException;

    public float readFloat() throws IOException;

    public double readDouble() throws IOException;

    public char readChar() throws IOException;

    public String readUTF() throws IOException;

    public void readFully(byte[] var1) throws IOException;

    public void readFully(byte[] var1, int var2, int var3) throws IOException;

    public int readUnsignedByte() throws IOException;

    public int readUnsignedShort() throws IOException;

    public void write(byte[] var1) throws IOException;

    public void write(int var1) throws IOException;

    public void write(byte[] var1, int var2, int var3) throws IOException;

    public void writeBoolean(boolean var1) throws IOException;

    public void writeByte(int var1) throws IOException;

    public void writeBytes(String var1) throws IOException;

    public void writeChar(int var1) throws IOException;

    public void writeChars(String var1) throws IOException;

    public void writeDouble(double var1) throws IOException;

    public void writeFloat(float var1) throws IOException;

    public void writeInt(int var1) throws IOException;

    public void writeLong(long var1) throws IOException;

    public void writeShort(int var1) throws IOException;

    public void writeUTF(String var1) throws IOException;

    public BufferedImage readImage() throws IOException;

    public void writeImage(BufferedImage var1, String var2) throws IOException;

    public DataOutputStream writer();

    public DataInputStream reader();

    public byte[] getData();

    public void cleanup();

    public void dispose();
}

