package nro.models.server.dispatch;

/** Exact signed byte used on the NRO wire protocol. */
public record CommandKey(byte wireValue) {

    public static CommandKey of(int command) {
        return new CommandKey((byte) command);
    }
}
