package nro.models.activity;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.services.Service;

/**
 * Versioned, bounded client contract for the advanced Activity Points screen.
 *
 * <p>The legacy {@code -97} packet stays untouched for old clients. New clients
 * explicitly request this snapshot through {@code -58}; old clients ignore that
 * command and can continue using the NPC menu.</p>
 */
public final class ActivityClientService {

    /** Unused in both legacy client controllers when this protocol was added. */
    public static final byte COMMAND = -58;
    public static final byte PROTOCOL_VERSION = 1;

    public static final byte REQUEST_OPEN = 0;
    public static final byte REQUEST_CLAIM = 1;
    public static final byte RESPONSE_SNAPSHOT = 0;

    public static final byte PERIOD_DAILY = 0;
    public static final byte PERIOD_WEEKLY = 1;

    private static final int MAX_TIER_COUNT = 16;
    private static final int MAX_SOURCE_COUNT = 32;
    private static final int MAX_TIER_ID_LENGTH = 64;

    private static final ActivityClientService INSTANCE = new ActivityClientService();

    private ActivityClientService() {
    }

    public static ActivityClientService gI() {
        return INSTANCE;
    }

    /** Handles only command {@link #COMMAND}; the controller owns dispatching. */
    public void handleRequest(Player player, Message message) throws IOException {
        if (player == null || message == null || message.reader() == null) {
            return;
        }
        int request = message.reader().readUnsignedByte();
        switch (request) {
            case REQUEST_OPEN -> sendSnapshot(player);
            case REQUEST_CLAIM -> claim(player, message);
            default -> Service.gI().sendThongBao(player, "Yêu cầu Năng động không hợp lệ.");
        }
    }

    public void sendSnapshot(Player player) {
        if (player == null) {
            return;
        }
        ActivityService.gI().ensureCurrentPeriod(player, false, true);
        ActivityConfig config = ActivityConfigService.gI().getCurrent();
        ActivityState state = player.activityState;
        if (state == null) {
            state = new ActivityState();
            player.activityState = state;
        }

        Message message = null;
        try {
            message = new Message(COMMAND);
            message.writer().writeByte(RESPONSE_SNAPSHOT);
            message.writer().writeByte(PROTOCOL_VERSION);
            message.writer().writeInt(safeInt(config.getRevision()));
            message.writer().writeByte(featureFlags(config));

            synchronized (state) {
                state.normalize();
                message.writer().writeInt(clampNonNegative(state.dailyPoints));
                message.writer().writeInt(clampNonNegative(config.getDailyMax()));
                message.writer().writeInt(clampNonNegative(state.weeklyPoints));
                message.writer().writeByte(clampByte(state.qualifiedDays));
                message.writer().writeLong(nextDailyReset(config));
                message.writer().writeLong(nextWeeklyReset(config));

                writeTiers(message, ActivityRewardService.gI().getTierStatuses(player, ActivityPeriodType.DAILY));
                writeTiers(message, ActivityRewardService.gI().getTierStatuses(player, ActivityPeriodType.WEEKLY));
                writeSources(message, config, state);
            }
            message.writer().flush();
            player.sendMessage(message);
        } catch (Exception exception) {
            // A dashboard failure must never affect gameplay or the legacy -97 sync.
        } finally {
            if (message != null) {
                message.cleanup();
            }
        }
    }

    private void claim(Player player, Message message) throws IOException {
        int periodValue = message.reader().readUnsignedByte();
        String tierId = message.reader().readUTF();
        if (tierId == null || tierId.isBlank() || tierId.length() > MAX_TIER_ID_LENGTH) {
            Service.gI().sendThongBao(player, "Mốc Năng động không hợp lệ.");
            sendSnapshot(player);
            return;
        }
        ActivityClaimResult result;
        if (periodValue == PERIOD_DAILY) {
            result = ActivityRewardService.gI().claimDaily(player, tierId);
        } else if (periodValue == PERIOD_WEEKLY) {
            result = ActivityRewardService.gI().claimWeekly(player, tierId);
        } else {
            Service.gI().sendThongBao(player, "Kỳ Năng động không hợp lệ.");
            sendSnapshot(player);
            return;
        }
        Service.gI().sendThongBao(player, result.getMessage());
        sendSnapshot(player);
    }

    private void writeTiers(Message message, List<ActivityRewardService.TierStatus> statuses) throws IOException {
        int count = Math.min(MAX_TIER_COUNT, statuses == null ? 0 : statuses.size());
        message.writer().writeByte(count);
        for (int index = 0; index < count; index++) {
            ActivityRewardService.TierStatus status = statuses.get(index);
            ActivityTier tier = status.getTier();
            message.writer().writeUTF(safeTierId(tier.getId()));
            message.writer().writeShort(clampShort(tier.getThreshold()));
            message.writer().writeByte(clampByte(tier.getMinQualifiedDays()));
            message.writer().writeByte(tierState(status));
        }
    }

    private void writeSources(Message message, ActivityConfig config, ActivityState state) throws IOException {
        ActivityType[] types = ActivityType.values();
        int count = Math.min(MAX_SOURCE_COUNT, types.length);
        message.writer().writeByte(count);
        for (int index = 0; index < count; index++) {
            ActivityType type = types[index];
            ActivitySourceRule rule = config.getSourceRule(type);
            message.writer().writeByte(index);
            message.writer().writeShort(clampShort(state.counter(type.name())));
            message.writer().writeShort(clampShort(rule.getDailyCap()));
            message.writer().writeByte(rule.isEnabled() ? 1 : 0);
            message.writer().writeByte(type.getCategory().ordinal());
        }
    }

    private byte tierState(ActivityRewardService.TierStatus status) {
        if (!status.getTier().isEnabled()) {
            return 3; // disabled
        }
        if (status.isClaimed()) {
            return 2; // claimed
        }
        return status.isEligible() ? (byte) 1 : (byte) 0; // claimable / locked
    }

    private int featureFlags(ActivityConfig config) {
        int flags = 0;
        if (config.isEnabled()) {
            flags |= 1;
        }
        if (config.isShadowMode()) {
            flags |= 1 << 1;
        }
        if (config.isRewardsEnabled()) {
            flags |= 1 << 2;
        }
        return flags;
    }

    private long nextDailyReset(ActivityConfig config) {
        ZonedDateTime now = Instant.now().atZone(config.getZoneId());
        ZonedDateTime next = now.toLocalDate().atTime(config.getDailyResetHour(), 0).atZone(config.getZoneId());
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return next.toEpochSecond();
    }

    private long nextWeeklyReset(ActivityConfig config) {
        ZonedDateTime now = Instant.now().atZone(config.getZoneId());
        DayOfWeek resetDay = config.getWeeklyResetDay();
        ZonedDateTime next = now.toLocalDate().with(TemporalAdjusters.nextOrSame(resetDay))
                .atTime(config.getDailyResetHour(), 0).atZone(config.getZoneId());
        if (!next.isAfter(now)) {
            next = next.plusWeeks(1);
        }
        return next.toEpochSecond();
    }

    private String safeTierId(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_TIER_ID_LENGTH ? value : value.substring(0, MAX_TIER_ID_LENGTH);
    }

    private int clampNonNegative(int value) {
        return Math.max(0, value);
    }

    private int clampShort(int value) {
        return Math.max(0, Math.min(Short.MAX_VALUE, value));
    }

    private int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private int safeInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, value);
    }
}
