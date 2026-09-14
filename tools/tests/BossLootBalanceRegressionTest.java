package nro.models.boss;

import nro.models.reward.BossLootBalance;
import nro.models.combine.AppearanceUpgradeCategory;
import nro.models.combine.GhepManhHuyDiet;
import nro.models.combine.NangCapNgoaiTrang;
import nro.models.combine.NangCapNhan;

/**
 * Executable regression contract for the economy-facing boss loot policy.
 * Run after compiling against the release JAR.
 */
public final class BossLootBalanceRegressionTest {

    private BossLootBalanceRegressionTest() {
    }

    public static void main(String[] args) {
        check(BossLootBalance.TRAIN_DRAGON_BALL_5_TO_7_RATE == 5,
                "5-7 star dragon balls stay at 5% on training maps");
        check(BossLootBalance.DUNGEON_DRAGON_BALL_4_STAR_RATE == 5,
                "4 star dragon ball stays at 5% in dungeons");
        check(BossLootBalance.UPGRADE_STONE_RATE == 7,
                "one random upgrade stone has a 7% map-drop roll");
        check(BossLootBalance.STONE_FRAGMENT_RATE == 10,
                "stone fragments have a 10% map-drop roll");
        check(BossLootBalance.BOSS_DRAGON_BALL_3_STAR_RATE == 30,
                "boss 3-star dragon ball rate is 30%");
        check(BossLootBalance.BOSS_DRAGON_BALL_2_STAR_RATE == 5,
                "boss 2-star dragon ball rate is 5%");
        check(BossLootBalance.regionForBoss(BossID.FIDE) == BossLootBalance.BossRegion.FIDE,
                "Fide gets the Fide reward table");
        check(BossLootBalance.regionForBoss(BossID.BLACK_GOKU) == BossLootBalance.BossRegion.FUTURE,
                "Black Goku gets the Future reward table");
        check(BossLootBalance.regionForBoss(BossID.COOLER) == BossLootBalance.BossRegion.COLD,
                "Cooler gets the Cold reward table");
        check(BossLootBalance.regionForBoss(BossID.BROLY) == BossLootBalance.BossRegion.NONE,
                "unrelated bosses do not receive a regional table");
        check(!BossLootBalance.hasMaximumLuck(99), "99% luck cannot double boss stones");
        check(BossLootBalance.hasMaximumLuck(100), "100% luck doubles boss stones");
        check(BossLootBalance.applyBossStoneLuck(5, 99) == 5,
                "below 100% luck leaves stone quantity unchanged");
        check(BossLootBalance.applyBossStoneLuck(5, 100) == 10,
                "100% luck doubles stone quantity");
        check(GhepManhHuyDiet.getDropSourceGuide(0).contains("Tương Lai"),
                "armor fragments explain their Future-boss source");
        check(GhepManhHuyDiet.getDropSourceGuide(4).contains("Hành Tinh Lạnh"),
                "ring fragments explain their Cold-boss source");
        check(NangCapNhan.getGoldenStoneSourceGuide().contains("Tương Lai")
                && NangCapNhan.getGoldenStoneSourceGuide().contains("Hành Tinh Lạnh"),
                "ring upgrade guide names both golden-stone sources");
        check(NangCapNgoaiTrang.getStoneSourceGuide(AppearanceUpgradeCategory.BACK).contains("Fide"),
                "back-item upgrade guide names the Fide stone source");
        check(NangCapNgoaiTrang.getStoneSourceGuide(AppearanceUpgradeCategory.MOUNT).contains("Tương Lai"),
                "mount upgrade guide names the Future stone source");

        System.out.println("BossLootBalanceRegressionTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
