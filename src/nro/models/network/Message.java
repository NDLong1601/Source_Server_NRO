package nro.models.network;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import nro.models.interfaces.IMessage;

public class Message implements IMessage {

    public byte command;
    private ByteArrayOutputStream os;
    private DataOutputStream dos;
    private ByteArrayInputStream is;
    private DataInputStream dis;

    public Message(int command) {
        this((byte) command);
    }

    public Message(byte command) {
        this.command = command;
        this.os = new ByteArrayOutputStream();
        this.dos = new DataOutputStream(this.os);
    }

    public Message(byte command, byte[] data) {
        this.command = command;
        this.is = new ByteArrayInputStream(data);
        this.dis = new DataInputStream(this.is);
    }

    @Override
    public DataOutputStream writer() {
        return this.dos;
    }

    @Override
    public DataInputStream reader() {
        return this.dis;
    }

    @Override
    public byte[] getData() {
        return this.os.toByteArray();
    }

    @Override
    public void cleanup() {
        try {
            if (this.is != null) {
                this.is.close();
            }
            if (this.os != null) {
                this.os.close();
            }
            if (this.dis != null) {
                this.dis.close();
            }
            if (this.dos != null) {
                this.dos.close();
            }
        } catch (IOException iOException) {
        }
    }

    @Override
    public void dispose() {
        this.cleanup();
        this.dis = null;
        this.is = null;
        this.dos = null;
        this.os = null;
    }

    @Override
    public int read() throws IOException {
        return this.reader().read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return this.reader().read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return this.reader().read(b, off, len);
    }

    @Override
    public boolean readBoolean() throws IOException {
        return this.reader().readBoolean();
    }

    @Override
    public byte readByte() throws IOException {
        return this.reader().readByte();
    }

    @Override
    public short readShort() throws IOException {
        return this.reader().readShort();
    }

    @Override
    public int readInt() throws IOException {
        return this.reader().readInt();
    }

    @Override
    public long readLong() throws IOException {
        return this.reader().readLong();
    }

    @Override
    public float readFloat() throws IOException {
        return this.reader().readFloat();
    }

    @Override
    public double readDouble() throws IOException {
        return this.reader().readDouble();
    }

    @Override
    public char readChar() throws IOException {
        return this.reader().readChar();
    }

    @Override
    public String readUTF() throws IOException {
        return this.reader().readUTF();
    }

    @Override
    public void readFully(byte[] b) throws IOException {
        this.reader().readFully(b);
    }

    @Override
    public void readFully(byte[] b, int off, int len) throws IOException {
        this.reader().readFully(b, off, len);
    }

    @Override
    public int readUnsignedByte() throws IOException {
        return this.reader().readUnsignedByte();
    }

    @Override
    public int readUnsignedShort() throws IOException {
        return this.reader().readUnsignedShort();
    }

    @Override
    public void write(byte[] b) throws IOException {
        this.writer().write(b);
    }

    @Override
    public void write(int b) throws IOException {
        this.writer().write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        this.writer().write(b, off, len);
    }

    @Override
    public void writeBoolean(boolean v) throws IOException {
        this.writer().writeBoolean(v);
    }

    @Override
    public void writeByte(int v) throws IOException {
        this.writer().writeByte(v);
    }

    @Override
    public void writeBytes(String s) throws IOException {
        this.writer().writeBytes(s);
    }

    @Override
    public void writeChar(int v) throws IOException {
        this.writer().writeChar(v);
    }

    @Override
    public void writeChars(String s) throws IOException {
        this.writer().writeChars(s);
    }

    @Override
    public void writeDouble(double v) throws IOException {
        this.writer().writeDouble(v);
    }

    @Override
    public void writeFloat(float v) throws IOException {
        this.writer().writeFloat(v);
    }

    @Override
    public void writeInt(int v) throws IOException {
        this.writer().writeInt(v);
    }

    @Override
    public void writeLong(long v) throws IOException {
        this.writer().writeLong(v);
    }

    @Override
    public void writeShort(int v) throws IOException {
        this.writer().writeShort(v);
    }

    @Override
    public void writeUTF(String str) throws IOException {
        this.writer().writeUTF(str);
    }

    @Override
    public BufferedImage readImage() throws IOException {
        int size = this.readInt();
        byte[] dataImage = new byte[size];
        this.read(dataImage);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(dataImage));
        return image;
    }

    @Override
    public void writeImage(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write((RenderedImage) image, format, baos);
        byte[] dataImage = baos.toByteArray();
        this.writeInt(dataImage.length);
        this.write(dataImage);
    }
}
