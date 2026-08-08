package nro.models.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import nro.models.interfaces.IKeySessionHandler;
import nro.models.interfaces.IMessageHandler;
import nro.models.interfaces.IMessageSendCollect;
import nro.models.interfaces.ISession;
import nro.models.utils.StringUtil;

public class Session implements ISession {

    private static ISession instance;
    private static int ID_INIT;

    private final int id = ID_INIT++;
    private final Socket socket;
    private final String ip;
    private byte[] KEYS = "NRO".getBytes();
    private boolean sentKey;
    private boolean connected;

    private Sender sender;
    private Collector collector;
    private final Thread tSender;
    private final Thread tCollector;

    private IKeySessionHandler keyHandler;

    public static ISession gI() throws Exception {
        if (instance == null) {
            throw new Exception("Instance has not been initialized!");
        }
        return instance;
    }

    public Session(Socket socket) {
        this.socket = socket;
        this.connected = true;

        try {
            this.socket.setSendBufferSize(0x100000);
            this.socket.setReceiveBufferSize(0x100000);
        } catch (SocketException ignored) {
        }

        this.ip = ((InetSocketAddress) socket.getRemoteSocketAddress()).getAddress().getHostAddress();

        this.sender = new Sender(this, socket);
        this.collector = new Collector(this, socket);

        this.tSender = new Thread(this.sender, "Sender - IP : " + ip);
        this.tCollector = new Thread(this.collector, "Collector - IP : " + ip);
    }

    @Override
    public ISession start() {
        this.tSender.start();
        this.tCollector.start();
        return this;
    }

    @Override
    public ISession startSend() {
        this.tSender.start();
        return this;
    }

    @Override
    public ISession startCollect() {
        this.tCollector.start();
        return this;
    }

    @Override
    public ISession setSendCollect(IMessageSendCollect collect) {
        this.sender.setSend(collect);
        this.collector.setCollect(collect);
        return this;
    }

    @Override
    public ISession setMessageHandler(IMessageHandler handler) {
        this.collector.setMessageHandler(handler);
        return this;
    }

    @Override
    public ISession setKeyHandler(IKeySessionHandler handler) {
        this.keyHandler = handler;
        return this;
    }

    @Override
    public void sendMessage(Message msg) {
        if (this.isConnected() && msg != null) {
            this.sender.sendMessage(msg);
        }
    }

    @Override
    public void doSendMessage(Message msg) throws Exception {
        this.sender.doSendMessage(msg);
    }

    @Override
    public void sendKey() throws Exception {
        if (this.keyHandler == null) {
            throw new Exception("Key handler has not been initialized!");
        }
        if (Network.gI().isRandomKey()) {
            this.KEYS = StringUtil.randomText(7).getBytes();
        }
        this.keyHandler.sendKey(this);
    }

    @Override
    public void setSentKey(boolean sent) {
        this.sentKey = sent;
    }

    @Override
    public boolean sentKey() {
        return this.sentKey;
    }

    @Override
    public boolean isConnected() {
        return this.connected;
    }

    @Override
    public long getID() {
        return this.id;
    }

    @Override
    public String getIP() {
        return this.ip;
    }

    @Override
    public byte[] getKey() {
        return this.KEYS;
    }

    @Override
    public int getNumMessages() {
        return this.isConnected() ? this.sender.getNumMessages() : -1;
    }

    @Override
    public void disconnect() {
        this.connected = false;
        this.sentKey = false;

        if (this.sender != null) {
            this.sender.close();
        }
        if (this.collector != null) {
            this.collector.close();
        }

        try {
            this.socket.close();
        } catch (IOException ignored) {
        }

        this.dispose();
    }

    @Override
    public void dispose() {
        if (this.sender != null) {
            this.sender.dispose();
        }
        if (this.collector != null) {
            this.collector.dispose();
        }

        if (this.tSender.isAlive()) {
            this.tSender.interrupt();
        }
        if (this.tCollector.isAlive()) {
            this.tCollector.interrupt();
        }

        SessionManager.gI().removeSession(this);
    }
}
