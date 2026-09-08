package nro.models.player;

/**
 * A single debit or credit leg within an atomic wallet batch operation.
 */
public final class WalletLeg {

    public enum Type {
        DEBIT,
        CREDIT
    }

    private final Type type;
    private final Currency currency;
    private final long amount;

    public WalletLeg(Type type, Currency currency, long amount) {
        if (type == null) {
            throw new IllegalArgumentException("Leg type must not be null");
        }
        if (currency == null) {
            throw new IllegalArgumentException("Leg currency must not be null");
        }
        this.type = type;
        this.currency = currency;
        this.amount = amount;
    }

    public static WalletLeg debit(Currency currency, long amount) {
        return new WalletLeg(Type.DEBIT, currency, amount);
    }

    public static WalletLeg credit(Currency currency, long amount) {
        return new WalletLeg(Type.CREDIT, currency, amount);
    }

    public Type getType() {
        return type;
    }

    public Currency getCurrency() {
        return currency;
    }

    public long getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        return "WalletLeg{"
                + "type=" + type
                + ", currency=" + currency
                + ", amount=" + amount
                + '}';
    }
}
