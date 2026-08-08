package nro.models.network;

import lombok.NonNull;
import lombok.Setter;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import nro.models.interfaces.IMessageHandler;
import nro.models.interfaces.ISession;

public class QueueHandler implements Runnable {

    private ISession session;
    private BlockingDeque<Message> messages;
    @Setter
    private IMessageHandler messageHandler;

    public QueueHandler(@NonNull ISession session) {
        try {
            this.session = session;
            this.messages = new LinkedBlockingDeque<>();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void run() {
        try {
            while (session.isConnected()) {
                while (!messages.isEmpty()) {
                    Message message = messages.poll(5, TimeUnit.SECONDS);
                    if (message != null) {
                        this.messageHandler.onMessage(this.session, message);
                        message.cleanup();
                    }
                }
                TimeUnit.MILLISECONDS.sleep(33);
            }
        } catch (Exception ignored) {
        }
    }

    public void addMessage(Message msg) {
        try {
            if (session.isConnected() && messages.size() < 500) {
                messages.add(msg);
            }
        } catch (Exception ignored) {
        }
    }

    public void close() {
        if (messages != null) {
            messages.clear();
        }
    }

    public void dispose() {
        this.session = null;
        this.messages = null;
    }
}
