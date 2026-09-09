package nro.models.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import nro.models.utils.Logger;

/**
 * Authoritative domain boundary for all mutations of player currency:
 * Gold, Gems, Rubies, and Coupons.
 *
 * All operations are synchronized on the owning {@link Inventory} instance to ensure
 * consistency across player DAO, trading, consignment, and other synchronized subsystems.
 */
public final class PlayerWallet {

    private static final AtomicLong NEXT_LOCK_ORDER = new AtomicLong();

    private final Inventory inventory;
    private final long lockOrder;

    public PlayerWallet(Inventory inventory) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.lockOrder = NEXT_LOCK_ORDER.incrementAndGet();
    }

    public WalletSnapshot getSnapshot() {
        synchronized (inventory) {
            return new WalletSnapshot(inventory.gold, inventory.gem, inventory.ruby, inventory.coupon);
        }
    }

    public long getBalance(Currency currency) {
        if (currency == null) {
            return 0L;
        }
        synchronized (inventory) {
            return getBalanceUnderLock(currency);
        }
    }

    public long getGold() {
        return getBalance(Currency.GOLD);
    }

    public int getGem() {
        return (int) getBalance(Currency.GEM);
    }

    public int getRuby() {
        return (int) getBalance(Currency.RUBY);
    }

    public int getCoupon() {
        return (int) getBalance(Currency.COUPON);
    }

    private long getBalanceUnderLock(Currency currency) {
        return switch (currency) {
            case GOLD -> inventory.gold;
            case GEM -> (long) inventory.gem;
            case RUBY -> (long) inventory.ruby;
            case COUPON -> (long) inventory.coupon;
        };
    }

    private void setBalanceUnderLock(Currency currency, long amount) {
        switch (currency) {
            case GOLD -> inventory.gold = amount;
            case GEM -> inventory.gem = (int) amount;
            case RUBY -> inventory.ruby = (int) amount;
            case COUPON -> inventory.coupon = (int) amount;
        }
        inventory.markChanged();
    }

    /**
     * Debits a strictly positive amount from the specified currency.
     */
    public WalletResult tryDebit(Currency currency, long amount, WalletMutationContext context) {
        if (currency == null) {
            return WalletResult.fail(WalletStatus.INVALID_AMOUNT, Currency.GOLD, amount, 0L, context, "Loại tiền không hợp lệ");
        }
        if (amount <= 0L) {
            return WalletResult.fail(WalletStatus.INVALID_AMOUNT, currency, amount, 0L, context, "Số lượng trừ phải lớn hơn 0");
        }

        synchronized (inventory) {
            if (!inventory.isActive()) {
                return WalletResult.fail(WalletStatus.INACTIVE, currency, amount,
                        getBalanceUnderLock(currency), context, "Hành trang không hoạt động");
            }

            long current = getBalanceUnderLock(currency);
            long max = currency.getMaxLimit();
            if (current < 0L || current > max) {
                Logger.error("[PlayerWallet] Corrupt balance: currency=" + currency
                        + ", current=" + current + ", max=" + max + ", requested debit=" + amount);
                return WalletResult.fail(WalletStatus.INVALID_BALANCE, currency, amount,
                        current, context, "Số dư không hợp lệ");
            }

            if (current < amount) {
                return WalletResult.fail(WalletStatus.INSUFFICIENT_FUNDS, currency, amount,
                        current, context, "Không đủ " + currency.getDisplayName());
            }

            long updated = current - amount;
            setBalanceUnderLock(currency, updated);
            return WalletResult.success(currency, amount, amount, current, updated, context);
        }
    }

    /**
     * Credits a strictly positive amount, rejecting the entire operation if the final balance
     * would exceed the configured cap or overflow.
     */
    public WalletResult tryCreditExact(Currency currency, long amount, WalletMutationContext context) {
        if (currency == null) {
            return WalletResult.fail(WalletStatus.INVALID_AMOUNT, Currency.GOLD, amount, 0L, context, "Loại tiền không hợp lệ");
        }
        if (amount <= 0L) {
            return WalletResult.fail(WalletStatus.INVALID_AMOUNT, currency, amount, 0L, context, "Số lượng cộng phải lớn hơn 0");
        }

        synchronized (inventory) {
            if (!inventory.isActive()) {
                return WalletResult.fail(WalletStatus.INACTIVE, currency, amount,
                        getBalanceUnderLock(currency), context, "Hành trang không hoạt động");
            }

            long current = getBalanceUnderLock(currency);
            long max = currency.getMaxLimit();
            if (current < 0L || current > max) {
                Logger.error("[PlayerWallet] Corrupt balance: currency=" + currency
                        + ", current=" + current + ", max=" + max + ", requested credit=" + amount);
                return WalletResult.fail(WalletStatus.INVALID_BALANCE, currency, amount,
                        current, context, "Số dư không hợp lệ");
            }

            long updated = current + amount;
            if (updated < current || updated > max) {
                return WalletResult.fail(WalletStatus.LIMIT_EXCEEDED, currency, amount,
                        current, context, "Vượt quá giới hạn " + currency.getDisplayName());
            }

            setBalanceUnderLock(currency, updated);
            return WalletResult.success(currency, amount, amount, current, updated, context);
        }
    }

    /**
     * Credits up to the configured limit, truncating if necessary.
     * Returns the actual applied amount in the result.
     */
    public WalletResult creditUpToCap(Currency currency, long amount, WalletMutationContext context) {
        if (currency == null) {
            return WalletResult.fail(WalletStatus.INVALID_AMOUNT, Currency.GOLD, amount, 0L, context, "Loại tiền không hợp lệ");
        }
        if (amount <= 0L) {
            return WalletResult.fail(WalletStatus.INVALID_AMOUNT, currency, amount, 0L, context, "Số lượng cộng phải lớn hơn 0");
        }

        synchronized (inventory) {
            if (!inventory.isActive()) {
                return WalletResult.fail(WalletStatus.INACTIVE, currency, amount,
                        getBalanceUnderLock(currency), context, "Hành trang không hoạt động");
            }

            long current = getBalanceUnderLock(currency);
            long max = currency.getMaxLimit();
            if (current < 0L || current > max) {
                Logger.error("[PlayerWallet] Corrupt balance: currency=" + currency
                        + ", current=" + current + ", max=" + max + ", requested capped credit=" + amount);
                return WalletResult.fail(WalletStatus.INVALID_BALANCE, currency, amount,
                        current, context, "Số dư không hợp lệ");
            }

            long availableSpace = Math.max(0L, max - current);
            long applied = Math.min(amount, availableSpace);
            long updated = current + applied;

            setBalanceUnderLock(currency, updated);
            return WalletResult.success(currency, amount, applied, current, updated, context);
        }
    }

    /**
     * Executes an atomic multi-leg batch mutation.
     * Validates every leg before changing any balance. Either all legs apply or nothing changes.
     */
    public WalletResult executeBatch(List<WalletLeg> legs, WalletMutationContext context) {
        if (legs == null || legs.isEmpty()) {
            return WalletResult.fail(WalletStatus.INVALID_AMOUNT, Currency.GOLD, 0L, 0L, context, "Danh sách thao tác rỗng");
        }

        synchronized (inventory) {
            if (!inventory.isActive()) {
                return WalletResult.fail(WalletStatus.INACTIVE, Currency.GOLD, 0L,
                        getBalanceUnderLock(Currency.GOLD), context, "Hành trang không hoạt động");
            }

            long nextGold = inventory.gold;
            long nextGem = (long) inventory.gem;
            long nextRuby = (long) inventory.ruby;
            long nextCoupon = (long) inventory.coupon;

            // Check corrupt live balances
            if (nextGold < 0L || nextGold > PlayerConfig.getMaxGold()
                    || nextGem < 0L || nextGem > (long) PlayerConfig.getMaxGem()
                    || nextRuby < 0L || nextRuby > (long) PlayerConfig.getMaxRuby()
                    || nextCoupon < 0L || nextCoupon > (long) PlayerConfig.getMaxCoupon()) {
                return WalletResult.fail(WalletStatus.INVALID_BALANCE, Currency.GOLD, 0L,
                        nextGold, context, "Số dư ví hiện tại không hợp lệ");
            }

            // Simulate and validate each leg
            for (WalletLeg leg : legs) {
                if (leg == null) {
                    return WalletResult.fail(WalletStatus.INVALID_AMOUNT, Currency.GOLD, 0L, nextGold, context, "Thao tác leg không hợp lệ");
                }
                long amt = leg.getAmount();
                if (amt <= 0L) {
                    return WalletResult.fail(WalletStatus.INVALID_AMOUNT, leg.getCurrency(), amt,
                            getBalanceUnderLock(leg.getCurrency()), context, "Số lượng phải lớn hơn 0");
                }

                Currency curr = leg.getCurrency();
                long max = curr.getMaxLimit();
                long currVal = switch (curr) {
                    case GOLD -> nextGold;
                    case GEM -> nextGem;
                    case RUBY -> nextRuby;
                    case COUPON -> nextCoupon;
                };

                if (leg.getType() == WalletLeg.Type.DEBIT) {
                    if (currVal < amt) {
                        return WalletResult.fail(WalletStatus.INSUFFICIENT_FUNDS, curr, amt,
                                currVal, context, "Không đủ " + curr.getDisplayName());
                    }
                    long nextVal = currVal - amt;
                    switch (curr) {
                        case GOLD -> nextGold = nextVal;
                        case GEM -> nextGem = nextVal;
                        case RUBY -> nextRuby = nextVal;
                        case COUPON -> nextCoupon = nextVal;
                    }
                } else {
                    long nextVal = currVal + amt;
                    if (nextVal < currVal || nextVal > max) {
                        return WalletResult.fail(WalletStatus.LIMIT_EXCEEDED, curr, amt,
                                currVal, context, "Vượt quá giới hạn " + curr.getDisplayName());
                    }
                    switch (curr) {
                        case GOLD -> nextGold = nextVal;
                        case GEM -> nextGem = nextVal;
                        case RUBY -> nextRuby = nextVal;
                        case COUPON -> nextCoupon = nextVal;
                    }
                }
            }

            // All legs valid: commit atomically
            long beforeGold = inventory.gold;
            inventory.gold = nextGold;
            inventory.gem = (int) nextGem;
            inventory.ruby = (int) nextRuby;
            inventory.coupon = (int) nextCoupon;
            inventory.markChanged();

            return WalletResult.success(Currency.GOLD, 0L, 0L, beforeGold, nextGold, context);
        }
    }

    /**
     * Prepares a planned mutation without applying it.
     */
    public PreparedWalletMutation prepare(List<WalletLeg> legs, WalletMutationContext context) {
        if (legs == null || legs.isEmpty()) {
            return null;
        }
        synchronized (inventory) {
            if (!inventory.isActive()) {
                return null;
            }
            WalletSnapshot before = getSnapshot();
            if (!isValidSnapshot(before)) {
                Logger.error("[PlayerWallet] Refused to prepare from corrupt balance: " + before);
                return null;
            }
            long nextGold = before.getGold();
            long nextGem = (long) before.getGem();
            long nextRuby = (long) before.getRuby();
            long nextCoupon = (long) before.getCoupon();

            for (WalletLeg leg : legs) {
                if (leg == null || leg.getAmount() <= 0L) {
                    return null;
                }
                Currency curr = leg.getCurrency();
                long max = curr.getMaxLimit();
                long currVal = switch (curr) {
                    case GOLD -> nextGold;
                    case GEM -> nextGem;
                    case RUBY -> nextRuby;
                    case COUPON -> nextCoupon;
                };

                if (leg.getType() == WalletLeg.Type.DEBIT) {
                    if (currVal < leg.getAmount()) {
                        return null;
                    }
                    long nextVal = currVal - leg.getAmount();
                    switch (curr) {
                        case GOLD -> nextGold = nextVal;
                        case GEM -> nextGem = nextVal;
                        case RUBY -> nextRuby = nextVal;
                        case COUPON -> nextCoupon = nextVal;
                    }
                } else {
                    long nextVal = currVal + leg.getAmount();
                    if (nextVal < currVal || nextVal > max) {
                        return null;
                    }
                    switch (curr) {
                        case GOLD -> nextGold = nextVal;
                        case GEM -> nextGem = nextVal;
                        case RUBY -> nextRuby = nextVal;
                        case COUPON -> nextCoupon = nextVal;
                    }
                }
            }

            WalletSnapshot after = new WalletSnapshot(nextGold, (int) nextGem, (int) nextRuby, (int) nextCoupon);
            return new PreparedWalletMutation(this, before, after, legs, context);
        }
    }

    /**
     * Prepares a single debit operation.
     */
    public PreparedWalletMutation prepareDebit(Currency currency, long amount, WalletMutationContext context) {
        return prepare(List.of(WalletLeg.debit(currency, amount)), context);
    }

    /**
     * Prepares a single credit operation.
     */
    public PreparedWalletMutation prepareCredit(Currency currency, long amount, WalletMutationContext context) {
        return prepare(List.of(WalletLeg.credit(currency, amount)), context);
    }

    /**
     * Applies an immutable prepared mutation after external persistent commit.
     * Verifies that the current live balances match the expected before-state.
     */
    public WalletResult applyCommitted(PreparedWalletMutation prepared) {
        if (prepared == null) {
            return WalletResult.fail(WalletStatus.INVALID_AMOUNT, Currency.GOLD, 0L, 0L, null, "Kế hoạch biến động ví null");
        }

        synchronized (inventory) {
            if (!prepared.belongsTo(this)) {
                Logger.error("[PlayerWallet] Rejected prepared mutation owned by another wallet");
                return WalletResult.fail(WalletStatus.UNAUTHORIZED_PLAN, Currency.GOLD, 0L,
                        inventory.gold, prepared.getContext(), "Kế hoạch không thuộc ví này");
            }
            if (!inventory.isActive()) {
                return WalletResult.fail(WalletStatus.INACTIVE, Currency.GOLD, 0L,
                        inventory.gold, prepared.getContext(), "Hành trang không hoạt động");
            }

            WalletSnapshot before = prepared.getBeforeSnapshot();
            WalletSnapshot after = prepared.getAfterSnapshot();
            if (before == null || after == null || prepared.getLegs().isEmpty()
                    || !isValidSnapshot(before) || !isValidSnapshot(after)
                    || !after.equals(simulate(before, prepared.getLegs()))) {
                Logger.error("[PlayerWallet] Rejected invalid prepared mutation: " + prepared);
                return WalletResult.fail(WalletStatus.UNAUTHORIZED_PLAN, Currency.GOLD, 0L,
                        inventory.gold, prepared.getContext(), "Kế hoạch biến động ví không hợp lệ");
            }

            WalletSnapshot current = getSnapshot();
            if (!current.equals(before)) {
                Logger.error("[PlayerWallet] Prepared mutation state conflict: expected="
                        + prepared.getBeforeSnapshot() + ", actual=" + current);
                return WalletResult.fail(WalletStatus.STATE_CHANGED, Currency.GOLD, 0L,
                        inventory.gold, prepared.getContext(), "Trạng thái ví đã thay đổi");
            }

            inventory.gold = after.getGold();
            inventory.gem = after.getGem();
            inventory.ruby = after.getRuby();
            inventory.coupon = after.getCoupon();
            inventory.markChanged();

            return WalletResult.success(Currency.GOLD, 0L, 0L, current.getGold(), after.getGold(), prepared.getContext());
        }
    }

    /**
     * Replaces live balances from an authoritative persistence snapshot.
     * This is deliberately exact: corrupt data is rejected rather than silently clamped.
     */
    public WalletResult restoreExact(WalletSnapshot snapshot, WalletMutationContext context) {
        if (snapshot == null || context == null || context.getReason() == null
                || !context.getReason().isRestricted()
                || context.getTransactionId() == null || context.getTransactionId().isBlank()) {
            return WalletResult.fail(WalletStatus.INVALID_AMOUNT, Currency.GOLD, 0L,
                    getGold(), context, "Ngữ cảnh khôi phục ví không hợp lệ");
        }
        synchronized (inventory) {
            if (!inventory.isActive()) {
                return WalletResult.fail(WalletStatus.INACTIVE, Currency.GOLD, 0L,
                        inventory.gold, context, "Hành trang không hoạt động");
            }
            if (!isValidSnapshot(snapshot)) {
                Logger.error("[PlayerWallet] Rejected corrupt persisted wallet snapshot: " + snapshot);
                return WalletResult.fail(WalletStatus.INVALID_BALANCE, Currency.GOLD, 0L,
                        inventory.gold, context, "Dữ liệu ví lưu trữ không hợp lệ");
            }
            long beforeGold = inventory.gold;
            inventory.gold = snapshot.getGold();
            inventory.gem = snapshot.getGem();
            inventory.ruby = snapshot.getRuby();
            inventory.coupon = snapshot.getCoupon();
            inventory.markChanged();
            return WalletResult.success(Currency.GOLD, 0L, 0L,
                    beforeGold, snapshot.getGold(), context);
        }
    }

    /**
     * Transfers currency between two player wallets with deterministic lock ordering
     * owned by the wallets to prevent deadlocks.
     */
    public static WalletResult transfer(PlayerWallet source, PlayerWallet target,
                                        Currency currency, long amount,
                                        WalletMutationContext context) {
        if (source == null || target == null || currency == null) {
            return WalletResult.fail(WalletStatus.INVALID_AMOUNT, Currency.GOLD, amount, 0L, context, "Tham số chuyển tiền không hợp lệ");
        }
        if (amount <= 0L) {
            return WalletResult.fail(WalletStatus.INVALID_AMOUNT, currency, amount, 0L, context, "Số lượng chuyển phải lớn hơn 0");
        }
        if (source == target || source.inventory == target.inventory) {
            return WalletResult.fail(WalletStatus.INVALID_AMOUNT, currency, amount,
                    source.getBalance(currency), context, "Không thể chuyển tiền cho cùng một ví");
        }

        // Order is owned by the wallets; callers cannot spoof it with player IDs.
        PlayerWallet first = source.lockOrder < target.lockOrder ? source : target;
        PlayerWallet second = source.lockOrder < target.lockOrder ? target : source;

        synchronized (first.inventory) {
            synchronized (second.inventory) {
                if (!source.inventory.isActive() || !target.inventory.isActive()) {
                    return WalletResult.fail(WalletStatus.INACTIVE, currency, amount,
                            source.getBalanceUnderLock(currency), context, "Hành trang không hoạt động");
                }

                long srcBal = source.getBalanceUnderLock(currency);
                long dstBal = target.getBalanceUnderLock(currency);
                long max = currency.getMaxLimit();

                if (srcBal < 0L || srcBal > max || dstBal < 0L || dstBal > max) {
                    return WalletResult.fail(WalletStatus.INVALID_BALANCE, currency, amount,
                            srcBal, context, "Số dư tài khoản không hợp lệ");
                }

                if (srcBal < amount) {
                    return WalletResult.fail(WalletStatus.INSUFFICIENT_FUNDS, currency, amount,
                            srcBal, context, "Không đủ " + currency.getDisplayName() + " để chuyển");
                }

                long dstNext = dstBal + amount;
                if (dstNext < dstBal || dstNext > max) {
                    return WalletResult.fail(WalletStatus.LIMIT_EXCEEDED, currency, amount,
                            srcBal, context, "Người nhận đã đạt giới hạn " + currency.getDisplayName());
                }

                source.setBalanceUnderLock(currency, srcBal - amount);
                target.setBalanceUnderLock(currency, dstNext);

                return WalletResult.success(currency, amount, amount, srcBal, srcBal - amount, context);
            }
        }
    }

    /**
     * Applies one batch to each of two distinct wallets as a single in-memory commit.
     * Both batches are simulated before either wallet is changed.
     */
    public static WalletResult executePair(PlayerWallet firstWallet, List<WalletLeg> firstLegs,
                                           PlayerWallet secondWallet, List<WalletLeg> secondLegs,
                                           WalletMutationContext context) {
        if (firstWallet == null || secondWallet == null
                || firstWallet == secondWallet || firstWallet.inventory == secondWallet.inventory
                || firstLegs == null || firstLegs.isEmpty()
                || secondLegs == null || secondLegs.isEmpty()) {
            return WalletResult.fail(WalletStatus.INVALID_AMOUNT, Currency.GOLD, 0L, 0L,
                    context, "Yêu cầu biến động hai ví không hợp lệ");
        }

        PlayerWallet firstLock = firstWallet.lockOrder < secondWallet.lockOrder
                ? firstWallet : secondWallet;
        PlayerWallet secondLock = firstWallet.lockOrder < secondWallet.lockOrder
                ? secondWallet : firstWallet;
        synchronized (firstLock.inventory) {
            synchronized (secondLock.inventory) {
                if (!firstWallet.inventory.isActive() || !secondWallet.inventory.isActive()) {
                    return WalletResult.fail(WalletStatus.INACTIVE, Currency.GOLD, 0L,
                            firstWallet.inventory.gold, context, "Một trong hai ví không hoạt động");
                }
                WalletSnapshot firstBefore = firstWallet.getSnapshot();
                WalletSnapshot secondBefore = secondWallet.getSnapshot();
                WalletSnapshot firstAfter = simulate(firstBefore, firstLegs);
                WalletSnapshot secondAfter = simulate(secondBefore, secondLegs);
                if (firstAfter == null || secondAfter == null) {
                    return WalletResult.fail(WalletStatus.INVALID_BALANCE, Currency.GOLD, 0L,
                            firstBefore.getGold(), context,
                            "Không thể áp dụng trọn vẹn biến động hai ví");
                }
                firstWallet.setSnapshotUnderLock(firstAfter);
                secondWallet.setSnapshotUnderLock(secondAfter);
                return WalletResult.success(Currency.GOLD, 0L, 0L,
                        firstBefore.getGold(), firstAfter.getGold(), context);
            }
        }
    }

    /** Compatibility overload; player IDs no longer participate in lock ordering. */
    public static WalletResult transfer(PlayerWallet source, PlayerWallet target,
                                        Currency currency, long amount,
                                        WalletMutationContext context,
                                        long sourcePlayerId, long targetPlayerId) {
        return transfer(source, target, currency, amount, context);
    }

    private static boolean isValidSnapshot(WalletSnapshot snapshot) {
        return snapshot != null
                && snapshot.getGold() >= 0L && snapshot.getGold() <= PlayerConfig.getMaxGold()
                && snapshot.getGem() >= 0 && snapshot.getGem() <= PlayerConfig.getMaxGem()
                && snapshot.getRuby() >= 0 && snapshot.getRuby() <= PlayerConfig.getMaxRuby()
                && snapshot.getCoupon() >= 0 && snapshot.getCoupon() <= PlayerConfig.getMaxCoupon();
    }

    private void setSnapshotUnderLock(WalletSnapshot snapshot) {
        inventory.gold = snapshot.getGold();
        inventory.gem = snapshot.getGem();
        inventory.ruby = snapshot.getRuby();
        inventory.coupon = snapshot.getCoupon();
        inventory.markChanged();
    }

    private static WalletSnapshot simulate(WalletSnapshot before, List<WalletLeg> legs) {
        if (!isValidSnapshot(before) || legs == null || legs.isEmpty()) {
            return null;
        }
        long gold = before.getGold();
        long gem = before.getGem();
        long ruby = before.getRuby();
        long coupon = before.getCoupon();
        for (WalletLeg leg : legs) {
            if (leg == null || leg.getAmount() <= 0L) {
                return null;
            }
            long current = switch (leg.getCurrency()) {
                case GOLD -> gold;
                case GEM -> gem;
                case RUBY -> ruby;
                case COUPON -> coupon;
            };
            long next;
            if (leg.getType() == WalletLeg.Type.DEBIT) {
                if (current < leg.getAmount()) {
                    return null;
                }
                next = current - leg.getAmount();
            } else {
                next = current + leg.getAmount();
                if (next < current || next > leg.getCurrency().getMaxLimit()) {
                    return null;
                }
            }
            switch (leg.getCurrency()) {
                case GOLD -> gold = next;
                case GEM -> gem = next;
                case RUBY -> ruby = next;
                case COUPON -> coupon = next;
            }
        }
        return new WalletSnapshot(gold, (int) gem, (int) ruby, (int) coupon);
    }
}
