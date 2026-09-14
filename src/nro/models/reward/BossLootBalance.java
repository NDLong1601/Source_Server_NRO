package nro.models.reward;

import nro.models.boss.BossID;

/**
 * Centralized, auditable drop rates for training maps and the named world bosses.
 * Rates are percentages; every reward roll is independent unless noted otherwise.
 */
public final class BossLootBalance {

    public static final int TRAIN_DRAGON_BALL_5_TO_7_RATE = 5;
    public static final int DUNGEON_DRAGON_BALL_4_STAR_RATE = 5;
    public static final int UPGRADE_STONE_RATE = 7;
    public static final int STONE_FRAGMENT_RATE = 10;

    public static final int BOSS_DRAGON_BALL_3_STAR_RATE = 30;
    public static final int BOSS_DRAGON_BALL_2_STAR_RATE = 5;

    public static final int FIDE_HEAVEN_STONE_RATE = 10;

    public static final int FUTURE_FLIGHT_STONE_RATE = 10;
    public static final int FUTURE_GOLDEN_STONE_RATE = 5;
    public static final int FUTURE_LEVEL_10_TO_12_EQUIPMENT_RATE = 10;
    public static final int FUTURE_DIVINE_EQUIPMENT_RATE = 5;
    public static final int FUTURE_DESTROY_FRAGMENT_RATE = 12;

    public static final int COLD_COSTUME_STONE_RATE = 8;
    public static final int COLD_PET_STONE_RATE = 10;
    public static final int COLD_GOLDEN_STONE_RATE = 5;
    public static final int COLD_LEVEL_10_TO_12_EQUIPMENT_RATE = 10;
    public static final int COLD_DIVINE_EQUIPMENT_RATE = 5;
    public static final int COLD_DESTROY_FRAGMENT_RATE = 12;

    public static final int[] FUTURE_DESTROY_FRAGMENT_IDS = {
        2027, 2028, 2029, 2030, 2031, 2032, 2033, 2034
    };
    public static final int[] COLD_DESTROY_FRAGMENT_IDS = {
        2035, 2036, 2037, 2038, 2039, 2040, 2041, 2042, 2043, 2044, 2045, 2046
    };

    public enum BossRegion {
        NONE,
        FIDE,
        FUTURE,
        COLD
    }

    private BossLootBalance() {
    }

    public static BossRegion regionForBoss(int bossId) {
        return switch (bossId) {
            case BossID.FIDE -> BossRegion.FIDE;
            case BossID.BLACK_GOKU -> BossRegion.FUTURE;
            case BossID.COOLER -> BossRegion.COLD;
            default -> BossRegion.NONE;
        };
    }

    public static boolean hasMaximumLuck(int luckPercent) {
        return luckPercent >= 100;
    }

    public static int applyBossStoneLuck(int quantity, int luckPercent) {
        if (quantity <= 0) {
            return 0;
        }
        return hasMaximumLuck(luckPercent) ? quantity * 2 : quantity;
    }
}
