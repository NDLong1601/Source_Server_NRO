package nro.models.network;

import nro.models.network.Message;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import nro.models.interfaces.IMessageSendCollect;
import nro.models.interfaces.ISession;

public class MessageSendCollect implements IMessageSendCollect {

    private int curR = 0;
    private int curW = 0;

    @Override
    public Message readMessage(ISession session, DataInputStream dis) throws Exception {
        int size;
        byte cmd = dis.readByte();
        if (session.sentKey()) {
            cmd = this.readKey(session, cmd);
        }
        if (session.sentKey()) {
            byte b1 = dis.readByte();
            byte b2 = dis.readByte();
            size = (this.readKey(session, b1) & 0xFF) << 8 | this.readKey(session, b2) & 0xFF;
        } else {
            size = dis.readUnsignedShort();
        }
        byte[] data = new byte[size];
        int len = 0;
        for (int byteRead = 0; len != -1 && byteRead < size; byteRead += len) {
            len = dis.read(data, byteRead, size - byteRead);
        }
        if (session.sentKey()) {
            for (int i = 0; i < data.length; ++i) {
                data[i] = this.readKey(session, data[i]);
            }
        }
        return new Message(cmd, data);
    }

    @Override
    public byte readKey(ISession session, byte b) {
        byte i = (byte) (session.getKey()[this.curR++] & 0xFF ^ b & 0xFF);
        if (this.curR >= session.getKey().length) {
            this.curR %= session.getKey().length;
        }
        return i;
    }

    @Override
    public void doSendMessage(ISession session, DataOutputStream dos, Message msg) throws Exception {
        try {
            byte[] data = msg.getData();
            if (session.sentKey()) {
                byte b = this.writeKey(session, msg.command);
                dos.writeByte(b);
            } else {
                dos.writeByte(msg.command);
            }
            if (data != null) {
                int size = data.length;
                if (msg.command == -32 || msg.command == -66 || msg.command == -74 || msg.command == 11 || msg.command == -67 || msg.command == -87 || msg.command == 66) {
                    byte b2 = this.writeKey(session, (byte) size);
                    dos.writeByte(b2 - 128);
                    byte b3 = this.writeKey(session, (byte) (size >> 8));
                    dos.writeByte(b3 - 128);
                    byte b4 = this.writeKey(session, (byte) (size >> 16));
                    dos.writeByte(b4 - 128);
                } else if (session.sentKey()) {
                    byte byte1 = this.writeKey(session, (byte) (size >> 8));
                    dos.writeByte(byte1);
                    byte byte2 = this.writeKey(session, (byte) (size & 0xFF));
                    dos.writeByte(byte2);
                } else {
                    dos.writeShort(size);
                }
                if (session.sentKey()) {
                    for (int i = 0; i < data.length; ++i) {
                        data[i] = this.writeKey(session, data[i]);
                    }
                }
                dos.write(data);
            } else {
                dos.writeShort(0);
            }
            dos.flush();
            msg.cleanup();
        } catch (IOException iOException) {
        }
    }

    @Override
    public byte writeKey(ISession session, byte b) {
        byte i = (byte) (session.getKey()[this.curW++] & 0xFF ^ b & 0xFF);
        if (this.curW >= session.getKey().length) {
            this.curW %= session.getKey().length;
        }
        return i;
    }
}
