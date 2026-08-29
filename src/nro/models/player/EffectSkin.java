package nro.models.player;

import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import nro.models.mob.Mob;
import nro.models.services.ItemService;
import nro.models.services.ItemTimeService;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import nro.models.services.InventoryService;
import nro.models.map.service.MapService;
import nro.models.utils.Util;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Setter;
import nro.models.services.EffectSkillService;
import nro.models.services.EquipmentOptionService;
import nro.models.services.SkillService;
import nro.models.skill.Skill;

/**
 *
 * @author By Mr Blue
 *
 */
public class EffectSkin {

    public static final String[] textOdo = new String[]{
        "Hôi quá, tránh xa ta ra", "Biến đi", "Trời ơi đồ ở dơ",
        "Thúi quá", "Mùi gì hôi quá"
    };

    public static final String[] textXinbato = new String[]{
        "Im đi, ông xinbato", "Thôi ông câm mẹ mồm đi", "Phân tâm quá",
        "Phân tâm quá", " Phân tâm quá"
    };

    private static final String[] textThoBulma = new String[]{
        "Wow, sexy quá"
    };

    private static final String[] textBuffSD = new String[]{
        "Wow! Sexy quá",};

    private static final Set<Integer> GOHAN_POC_ARALE_COSTUMES = Set.of(912, 913, 914);
    private static final Set<Integer> FOOTBALL_COSTUMES = Set.of(968, 969, 970, 971, 972, 973,
            974, 975, 976, 977, 978);
    private static final Set<Integer> SUMMER_COSTUMES = Set.of(1010, 1011, 1012);
    private static final Set<Integer> SUPERHERO_COSTUMES = Set.of(989, 990, 991);
    private static final Set<Integer> BUNNY_COSTUMES = Set.of(1041, 1042, 1043);
    /** Every costume used by the three Demon Slayer collection achievements. */
    private static final Set<Integer> DEMON_SLAYER_COSTUMES = Set.of(1087, 1088, 1089, 1091,
            2062, 2063, 2064, 2065, 2066, 2067, 2068, 2069, 2070, 2071, 2072, 2073, 2075);

    @Setter
    private Player player;
    private int tlNeDon;

    public EffectSkin(Player player) {
        this.player = player;
        this.xHPKI = 1;
        this.xDame = 1;
    }

    public long lastTimeAttack;
    /** Last time this player actually attacked a normal monster. */
    public long lastTimeAttackMob;
    public long lastTimeVoHinh;
    private long lastTimeOdo;
    private long lastTimeXinbato;
    private long lastTimeltdb;
    private long lastTimeThoBulma;
    private long lastTimeMaPhongBa;
    private long lastTimeXenHutHpKi;

    public long lastTimeAddTimeTrainArmor;
    public long lastTimeSubTimeTrainArmor;

    public boolean isVoHinh;

    public long lastTimeXHPKI;
    public int xHPKI;

    public long lastTimeXDame;
    public int xDame;

    public long lastTimeUpdateCTHT;

    private long lastTimeTanHinh;
    private long lastTimeLamCham;
    private long lastTimeHoaDa;
    private long lastTimeBienSocola;
    private long lastTimeBienCarrot;
    private long lastTimeBienPumpkin;
    private long lastTimeUpdateCostumeProximity;
    private long lastTimeCuteMpRecovery;
    private long lastTimeOption178Recovery;
    private long lastTimeOption221Recovery;
    private long lastTimeOption222Activation;
    private long option222ActiveUntil;
    private boolean option222ActiveState;
    private long lastTimeOption223Activation;
    private long option223ActiveUntil;
    private long lastTimeOption223Heal;
    private int option223HealTicks;
    private long lastTimeOption224Activation;
    private long option224ActiveUntil;
    private long lastTimeOption229Cold;
    private long lastTimeHalloween;
    public long lastTimeXChuong;
    public boolean isXChuong;
    public boolean isXDame;
    /** Runtime bonuses for options 145-146. */
    public int drSlumDamageBonus;
    public int drSlumSpeedBonus;
    /** Option 150 condition: another Android Sát Thủ costume is nearby. */
    public boolean androidProximityActive;
    /** Combined proximity bonuses for options 151, 152 and 176-180. */
    public int groupCostumeDamageBonus;
    public int groupCostumeSpeedBonus;
    public int groupCostumeAllStatBonus;
    /** Options 201-203 become active with at least two nearby clan members. */
    public int clanProximityDamageBonus;
    public int clanProximityHpBonus;
    public int clanProximityKiBonus;
    /** Option 249: HP granted by a nearby Hắc hóa costume. */
    public int darkHpAuraBonus;
    /** True while this player is within an enemy option-167 cold aura. */
    public boolean coldAuraAffected;
    /** Option 156's consecutive punch bonus. */
    public int punchComboPercent;
    private int punchComboMaxPercent;
    /** Runtime state for option 168. */
    public int absorbStackCount;
    public long absorbedDamage;
    public long lastTimeAbsorbBurst;
    public long lastTimeFireStrike;
    public long lastTimePoisonStrike;
    /** Prevents option-generated damage from receiving attack multipliers again. */
    public boolean applyingEquipmentOptionDamage;
    private long lastTimeTokuda;

    public void update() {
        updateVoHinh();
        if (!this.player.isDie() && this.player.zone != null && !MapService.gI().isMapOffline(this.player.zone.map.mapId)) {
            updateOdo();
            updateXinbato();
            updateThoBulma();
            updateMaPhongBa();
            updateXenHutXungQuanh();
            updateTanHinh();
            updateHoaDa();
            updateLamCham();
            updateBienSocola();
            updateBienCarrot();
            updateBienPumpkin();
            updateCostumeProximity();
            updateCuteMpRecovery();
            updateOption178Recovery();
            updateOption221Recovery();
            updateOption222Rage();
            updateOption223Recovery();
            updateOption224CooldownRush();
            updateOption229ColdAura();
            updateXChuong();
//            updateHalloween();
        }
        if (!this.player.isBoss && !this.player.isPet && !player.isNewPet) {
            updateTrainArmor();
        }
        if (xHPKI != 1 && Util.canDoWithTime(lastTimeXHPKI, 1800000)) {
            xHPKI = 1;
            Service.gI().point(player);
        }
        if (xDame != 1 && Util.canDoWithTime(lastTimeXDame, 1800000)) {
            xDame = 1;
            Service.gI().point(player);
        }
        updateCTHaiTac();
    }

    private void updateCTHaiTac() {
        if (this.player.setClothes != null && this.player.setClothes.ctHaiTac != -1
                && this.player.zone != null
                && Util.canDoWithTime(lastTimeUpdateCTHT, 5000)) {
            int count = 0;
            int[] cts = new int[9];
            cts[this.player.setClothes.ctHaiTac - 618] = this.player.setClothes.ctHaiTac;
            List<Player> players = new ArrayList<>();
            players.add(player);
            try {
                for (Player pl : player.zone.getNotBosses()) {
                    if (!player.equals(pl) && pl.setClothes.ctHaiTac != -1 && Util.getDistance(player, pl) <= 300) {
                        cts[pl.setClothes.ctHaiTac - 618] = pl.setClothes.ctHaiTac;
                        players.add(pl);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            for (int i = 0; i < cts.length; i++) {
                if (cts[i] != 0) {
                    count++;
                }
            }
            for (Player pl : players) {
                Item ct = pl.inventory.itemsBody.get(5);
                if (ct.isNotNullItem() && ct.template.id >= 618 && ct.template.id <= 626) {
                    for (Item.ItemOption io : ct.itemOptions) {
                        if (io.optionTemplate.id == 147
                                || io.optionTemplate.id == 77
                                || io.optionTemplate.id == 103) {
                            io.param = count * 3;
                        }
                    }
                }
                if (!pl.isPet && !pl.isNewPet && Util.canDoWithTime(lastTimeUpdateCTHT, 5000)) {
                    InventoryService.gI().sendItemBody(pl);
                }
                pl.effectSkin.lastTimeUpdateCTHT = System.currentTimeMillis();
            }
        }
    }

    /** Evaluates every costume/aura option whose condition depends on nearby players. */
    private void updateCostumeProximity() {
        if (!this.player.isPl() || this.player.zone == null
                || !Util.canDoWithTime(lastTimeUpdateCostumeProximity, 500)) {
            return;
        }
        Item costume = getEquippedCostume(this.player);
        boolean wearingDrSlum = hasOption(costume, 145) || hasOption(costume, 146);
        boolean wearingAndroid = hasOption(costume, 150);
        boolean wearingPilap = hasOption(costume, 151) || hasOption(costume, 152);
        boolean wearingPirate = hasOption(costume, 176);
        boolean wearingMabuBonus = hasOption(costume, 177);
        boolean wearingDemonsFrost = hasOption(costume, 179);
        int blackGohanBonus = getOptionParam(costume, 180);
        int gohanPocAraleBonus = getOptionParam(costume, 184);
        int footballBonus = getOptionParam(costume, 193);
        int summerBonus = getOptionParam(costume, 194);
        int superheroBonus = getOptionParam(costume, 195);
        int bunnyBonus = getOptionParam(costume, 196);
        int demonSlayerBonus = getOptionParam(costume, 205);
        Set<Integer> differentDrSlumCostumes = new HashSet<>();
        Set<Integer> differentPilapCostumes = new HashSet<>();
        Set<Integer> pirateCostumes = new HashSet<>();
        Set<Integer> demonsFrostCostumes = new HashSet<>();
        boolean hasDifferentAndroidCostume = false;
        int nearbyFatMabu = 0;
        boolean nearBlackGohanRose = false;
        boolean newColdAuraAffected = false;
        int nearbyGohanPocArale = 0;
        int nearbyFootball = 0;
        int nearbySummer = 0;
        int nearbySuperhero = 0;
        int nearbyBunny = 0;
        int nearbyDemonSlayer = 0;
        int nearbyClanMembers = 0;
        int newDarkHpAuraBonus = 0;

        if (wearingPirate && costume != null) {
            pirateCostumes.add((int) costume.template.id);
        }

        for (Player nearby : this.player.zone.getHumanoids()) {
                if (nearby == null || nearby.equals(this.player) || !nearby.isPl() || nearby.isDie()
                        || Util.getDistance(this.player, nearby) > 200) {
                    continue;
                }
                Item nearbyCostume = getEquippedCostume(nearby);
                if (wearingDrSlum && (hasOption(nearbyCostume, 145) || hasOption(nearbyCostume, 146))
                        && nearbyCostume.template.id != costume.template.id) {
                    differentDrSlumCostumes.add((int) nearbyCostume.template.id);
                }
                if (wearingAndroid && hasOption(nearbyCostume, 150)
                        && nearbyCostume.template.id != costume.template.id) {
                    hasDifferentAndroidCostume = true;
                }
                if (wearingPilap && (hasOption(nearbyCostume, 151) || hasOption(nearbyCostume, 152))
                        && nearbyCostume.template.id != costume.template.id) {
                    differentPilapCostumes.add((int) nearbyCostume.template.id);
                }
                if (wearingPirate && hasOption(nearbyCostume, 176)) {
                    pirateCostumes.add((int) nearbyCostume.template.id);
                }
                if (wearingMabuBonus && isFatMabu(nearby, nearbyCostume)) {
                    nearbyFatMabu++;
                }
                if (wearingDemonsFrost && isDemonsFrostCostume(nearbyCostume)) {
                    demonsFrostCostumes.add((int) nearbyCostume.template.id);
                }
                if (blackGohanBonus > 0 && isBlackGohanRose(nearby, nearbyCostume)) {
                    nearBlackGohanRose = true;
                }
                if (gohanPocAraleBonus > 0 && isCostumeInFamily(nearbyCostume, GOHAN_POC_ARALE_COSTUMES)) {
                    nearbyGohanPocArale++;
                }
                if (footballBonus > 0 && isCostumeInFamily(nearbyCostume, FOOTBALL_COSTUMES)) {
                    nearbyFootball++;
                }
                if (summerBonus > 0 && isCostumeInFamily(nearbyCostume, SUMMER_COSTUMES)) {
                    nearbySummer++;
                }
                if (superheroBonus > 0 && isCostumeInFamily(nearbyCostume, SUPERHERO_COSTUMES)) {
                    nearbySuperhero++;
                }
                if (bunnyBonus > 0 && isCostumeInFamily(nearbyCostume, BUNNY_COSTUMES)) {
                    nearbyBunny++;
                }
                if (demonSlayerBonus > 0 && isCostumeInFamily(nearbyCostume, DEMON_SLAYER_COSTUMES)) {
                    nearbyDemonSlayer++;
                }
                if (this.player.clan != null && nearby.clan == this.player.clan) {
                    nearbyClanMembers++;
                }
                newDarkHpAuraBonus = Math.max(newDarkHpAuraBonus,
                        getEquippedOptionParam(nearby, 249));
                if (nearby.nPoint != null && nearby.nPoint.hasColdAura
                        && SkillService.gI().canAttackPlayer(nearby, this.player)) {
                    newColdAuraAffected = true;
                }
            }

        int differentCount = Math.min(2, differentDrSlumCostumes.size());
        int newDrSlumDamage = differentCount == 0 ? 0 : (differentCount == 1 ? 20 : 30);
        int newDrSlumSpeed = differentCount == 0 ? 0 : (differentCount == 1 ? 66 : 100);
        boolean newAndroidActive = wearingAndroid && hasDifferentAndroidCostume;

        int pilapCount = Math.min(2, differentPilapCostumes.size());
        int pilapDamage = pilapCount == 0 ? 0 : pilapCount == 1 ? 20 : 30;
        int pilapSpeed = pilapCount == 0 ? 0 : pilapCount == 1 ? 66 : 100;
        int pirateDamage = wearingPirate && pirateCostumes.size() >= 5 ? 20 : 0;
        int pirateSpeed = wearingPirate && pirateCostumes.size() >= 5 ? 50 : 0;
        int mabuAllStat = wearingMabuBonus ? Math.min(2, nearbyFatMabu) : 0;
        int demonsFrostDamage = wearingDemonsFrost ? Math.min(10, demonsFrostCostumes.size() * 2) : 0;
        int blackGohanDamage = nearBlackGohanRose ? blackGohanBonus : 0;
        int legacyCostumeDamage = cappedNearbyBonus(gohanPocAraleBonus, nearbyGohanPocArale, 10)
                + cappedNearbyBonus(footballBonus, nearbyFootball, 11)
                + cappedNearbyBonus(summerBonus, nearbySummer, 10)
                + cappedNearbyBonus(superheroBonus, nearbySuperhero, 10)
                + cappedNearbyBonus(bunnyBonus, nearbyBunny, 18)
                + cappedNearbyBonus(demonSlayerBonus, nearbyDemonSlayer, 18);
        int newGroupDamage = pilapDamage + pirateDamage + demonsFrostDamage + blackGohanDamage
                + legacyCostumeDamage;
        int newGroupSpeed = pilapSpeed + pirateSpeed;
        boolean clanBonusActive = nearbyClanMembers >= 2;
        int newClanDamage = clanBonusActive ? getEquippedOptionParam(this.player, 201) : 0;
        int newClanHp = clanBonusActive ? getEquippedOptionParam(this.player, 202) : 0;
        int newClanKi = clanBonusActive ? getEquippedOptionParam(this.player, 203) : 0;

        if (newDrSlumDamage != drSlumDamageBonus || newDrSlumSpeed != drSlumSpeedBonus
                || newAndroidActive != androidProximityActive
                || newGroupDamage != groupCostumeDamageBonus
                || newGroupSpeed != groupCostumeSpeedBonus
                || mabuAllStat != groupCostumeAllStatBonus
                || newClanDamage != clanProximityDamageBonus
                || newClanHp != clanProximityHpBonus
                || newClanKi != clanProximityKiBonus
                || newDarkHpAuraBonus != darkHpAuraBonus
                || newColdAuraAffected != coldAuraAffected) {
            drSlumDamageBonus = newDrSlumDamage;
            drSlumSpeedBonus = newDrSlumSpeed;
            androidProximityActive = newAndroidActive;
            groupCostumeDamageBonus = newGroupDamage;
            groupCostumeSpeedBonus = newGroupSpeed;
            groupCostumeAllStatBonus = mabuAllStat;
            clanProximityDamageBonus = newClanDamage;
            clanProximityHpBonus = newClanHp;
            clanProximityKiBonus = newClanKi;
            darkHpAuraBonus = newDarkHpAuraBonus;
            coldAuraAffected = newColdAuraAffected;
            if (this.player.nPoint != null) {
                this.player.nPoint.calPoint();
                Service.gI().point(this.player);
                Service.gI().Send_Info_NV(this.player);
                Service.gI().sendSpeedPlayer(this.player, -1);
            }
        }
        lastTimeUpdateCostumeProximity = System.currentTimeMillis();
    }

    private Item getEquippedCostume(Player pl) {
        if (pl == null || pl.inventory == null || pl.inventory.itemsBody.size() <= 5) {
            return null;
        }
        Item costume = pl.inventory.itemsBody.get(5);
        return costume != null && costume.isNotNullItem() ? costume : null;
    }

    private boolean hasOption(Item item, int optionId) {
        if (item == null || item.itemOptions == null) {
            return false;
        }
        for (ItemOption option : item.itemOptions) {
            if (option != null && option.optionTemplate != null && option.optionTemplate.id == optionId) {
                return true;
            }
        }
        return false;
    }

    private int getOptionParam(Item item, int optionId) {
        if (item == null || item.itemOptions == null) {
            return 0;
        }
        int total = 0;
        for (ItemOption option : item.itemOptions) {
            if (option != null && option.optionTemplate != null && option.optionTemplate.id == optionId) {
                total += Math.max(0, option.param);
            }
        }
        return total;
    }

    private boolean isCostumeInFamily(Item costume, Set<Integer> family) {
        return costume != null && costume.template != null && family.contains((int) costume.template.id);
    }

    private int getEquippedOptionParam(Player target, int optionId) {
        if (target == null || target.inventory == null || target.inventory.itemsBody == null) {
            return 0;
        }
        int total = 0;
        for (Item item : target.inventory.itemsBody) {
            total += getOptionParam(item, optionId);
        }
        return Math.max(0, total);
    }

    private int cappedNearbyBonus(int bonusPerNearbyPlayer, int nearbyCount, int maximum) {
        if (bonusPerNearbyPlayer <= 0 || nearbyCount <= 0 || maximum <= 0) {
            return 0;
        }
        return Math.min(maximum, bonusPerNearbyPlayer * nearbyCount);
    }

    private boolean isFatMabu(Player nearby, Item nearbyCostume) {
        return nearbyCostume != null && nearbyCostume.template.id == 578
                || nearby.getHead() == 297 && nearby.getBody() == 298 && nearby.getLeg() == 299;
    }

    private boolean isDemonsFrostCostume(Item costume) {
        if (costume == null) {
            return false;
        }
        int id = costume.template.id;
        return hasOption(costume, 179) || id == 629 || id == 630 || id == 631 || id == 632
                || id == 878 || id == 879 || id == 1205 || id == 1599 || id == 2076;
    }

    private boolean isBlackGohanRose(Player nearby, Item costume) {
        return costume != null && costume.template.id == 883
                || nearby.getHead() == 879 && nearby.getBody() == 880 && nearby.getLeg() == 881;
    }

    private void updateXenHutXungQuanh() {
        try {
            if (this.player.nPoint != null && (player.nPoint.hp < player.nPoint.hpMax || player.nPoint.mp < player.nPoint.mpMax)) {
                int param = this.player.nPoint.tlHutHpMpXQ;
                if (param > 0) {
                    if (!this.player.isDie() && Util.canDoWithTime(lastTimeXenHutHpKi, 5000)) {
                        int hpHut = 0;
                        int mpHut = 0;
                        List<Player> players = new ArrayList<>();
                        List<Player> playersMap = this.player.zone.getNotBosses();
                        for (Player pl : playersMap) {
                            if (!this.player.equals(pl) && !pl.isBoss && !pl.isDie()
                                    && Util.getDistance(this.player, pl) <= 200) {
                                players.add(pl);
                            }

                        }
                        for (Mob mob : this.player.zone.mobs) {
                            if (mob.point.gethp() > 1) {
                                if (Util.getDistance(this.player, mob) <= 200) {
                                    int subHp = mob.point.getHpFull() * param / 100;
                                    if (subHp >= mob.point.gethp()) {
                                        subHp = mob.point.gethp() - 1;
                                    }
                                    hpHut += subHp;
                                    mob.injured(null, subHp, false);
                                }
                            }
                        }
                        for (Player pl : players) {
                            int subHp = (int) ((long) pl.nPoint.hpMax * param / 100);
                            int subMp = (int) ((long) pl.nPoint.mpMax * param / 100);
                            if (subHp >= pl.nPoint.hp) {
                                subHp = pl.nPoint.hp - 1;
                            }
                            if (subMp >= pl.nPoint.mp) {
                                subMp = pl.nPoint.mp - 1;
                            }
                            hpHut += subHp;
                            mpHut += subMp;
                            PlayerService.gI().sendInfoHpMpMoney(pl);
                            Service.gI().Send_Info_NV(pl);
                            pl.injured(null, subHp, true, false);
                        }
                        this.player.nPoint.addHp(hpHut);
                        this.player.nPoint.addMp(mpHut);
                        PlayerService.gI().sendInfoHpMpMoney(this.player);
                        Service.gI().Send_Info_NV(this.player);
                        this.lastTimeXenHutHpKi = System.currentTimeMillis();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateOdo() {
        try {
            if (this.player.nPoint != null) {
                int param = this.player.nPoint.tlHpGiamODo;
                if (param > 0) {
                    if (Util.canDoWithTime(lastTimeOdo, 10000)) {
                        List<Player> playersMap = this.player.zone.getNotBosses();
                        for (int i = playersMap.size() - 1; i >= 0; i--) {
                            Player pl = playersMap.get(i);
                            if (pl != null && pl.nPoint != null && !this.player.equals(pl) && !pl.isBoss && !pl.isDie()
                                    && Util.getDistance(this.player, pl) <= 200) {
                                int subHp = (int) ((long) pl.nPoint.hpMax * param / 100);
                                if (subHp >= pl.nPoint.hp) {
                                    subHp = pl.nPoint.hp - 1;
                                }
                                Service.gI().chat(pl, textOdo[Util.nextInt(0, textOdo.length - 1)]);
                                PlayerService.gI().sendInfoHpMpMoney(pl);
                                Service.gI().Send_Info_NV(pl);
                                pl.injured(null, subHp, true, false);
                            }

                        }
                        this.lastTimeOdo = System.currentTimeMillis();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateXinbato() {
        try {
            if (this.player.nPoint == null) {
                return;
            }

            boolean hasXinbato = false;
            for (Item item : player.inventory.itemsBody) {
                if (item != null && item.itemOptions != null) {
                    for (ItemOption io : item.itemOptions) {
                        if (io.optionTemplate.id == 111) {
                            hasXinbato = true;
                            break;
                        }
                    }
                }
                if (hasXinbato) {
                    break;
                }
            }

            if (!hasXinbato) {
                return;
            }

            if (!Util.canDoWithTime(lastTimeXinbato, 10_000)) {
                return;
            }

            int addNeDon = 50;

            List<Player> playersMap = this.player.zone.getPlayers();
            for (Player pl : playersMap) {
                if (pl != null && pl.nPoint != null && !pl.isDie()
                        && Util.getDistance(this.player, pl) <= 200) {

                    int baseNeDon = pl.nPoint.tlNeDon;

                    int buffNeDon = Math.min(addNeDon, 90 - baseNeDon);
                    if (buffNeDon > 0) {
                        pl.nPoint.tlNeDonBuffXinbato = buffNeDon;
                        pl.nPoint.timeXinbatoBuff = System.currentTimeMillis();
                        Service.gI().chat(pl, textXinbato[Util.nextInt(0, textXinbato.length - 1)]);
                        PlayerService.gI().sendInfoHpMpMoney(pl);
                        Service.gI().Send_Info_NV(pl);
                    }
                }
            }

            if (!this.player.isDie()) {
                int baseNeDon = this.player.nPoint.tlNeDon;
                int buffNeDon = Math.min(addNeDon, 90 - baseNeDon);
                if (buffNeDon > 0) {
                    this.player.nPoint.tlNeDonBuffXinbato = buffNeDon;
                    this.player.nPoint.timeXinbatoBuff = System.currentTimeMillis();
                    Service.gI().chat(this.player, textXinbato[Util.nextInt(0, textXinbato.length - 1)]);
                    PlayerService.gI().sendInfoHpMpMoney(this.player);
                    Service.gI().Send_Info_NV(this.player);
                }
            }

            this.lastTimeXinbato = System.currentTimeMillis();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateThoBulma() {
        try {
            if (this.player.nPoint != null && this.player.nPoint.tlSexyDame > 0) {
                if (Util.canDoWithTime(lastTimeThoBulma, 10000)) {

                    List<Player> playersMap = this.player.zone.getNotBosses();
                    for (int i = playersMap.size() - 1; i >= 0; i--) {
                        Player pl = playersMap.get(i);
                        if (pl != null && pl.nPoint != null && !this.player.equals(pl) && !pl.isBoss && !pl.isDie()
                                && Util.getDistance(this.player, pl) <= 200) {
                            if (player.nPoint.isThoBulma) {
                                Service.gI().chat(pl, textThoBulma[Util.nextInt(0, textThoBulma.length - 1)]);
                            } else {
                                Service.gI().chat(pl, textBuffSD[Util.nextInt(0, textBuffSD.length - 1)]);
                            }
                            EffectSkillService.gI().setDameBuff(pl, 11000, player.nPoint.tlSexyDame);
                        }
                    }
                    this.lastTimeThoBulma = System.currentTimeMillis();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateTanHinh() {
        try {
            if (this.player.nPoint != null && this.player.nPoint.isTanHinh) {
                if (Util.canDoWithTime(lastTimeTanHinh, 5000)) {
                    EffectSkillService.gI().setIsTanHinh(player, 1500);
                    this.lastTimeTanHinh = System.currentTimeMillis();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateHoaDa() {
        try {
            if (this.player.nPoint != null && this.player.nPoint.isHoaDa) {
                if (Util.canDoWithTime(lastTimeHoaDa, 30000)) {
                    List<Player> playersMap = this.player.zone.getNotBosses();
                    for (int i = playersMap.size() - 1; i >= 0; i--) {
                        Player pl = playersMap.get(i);
                        if (pl != null && pl.nPoint != null && !this.player.equals(pl) && !pl.isBoss && !pl.isDie()
                                && Util.getDistance(this.player, pl) <= 200) {
                            EffectSkillService.gI().setIsStone(pl, 6000);
                        }

                    }
                    this.lastTimeHoaDa = System.currentTimeMillis();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateLamCham() {
        try {
            if (this.player.nPoint != null && this.player.nPoint.isLamCham) {
                if (Util.canDoWithTime(lastTimeLamCham, 1000)) {

                    List<Player> playersMap = this.player.zone.getNotBosses();
                    for (int i = playersMap.size() - 1; i >= 0; i--) {
                        Player pl = playersMap.get(i);
                        if (pl != null && pl.isPl() && pl.nPoint != null && !this.player.equals(pl) && !pl.isDie()
                                && Util.getDistance(this.player, pl) <= 1000) {
                            EffectSkillService.gI().setIsLamCham(pl, 1500, 50);
                        }

                    }
                    this.lastTimeLamCham = System.currentTimeMillis();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateBienSocola() {
        try {
            if (this.player.nPoint == null || !this.player.nPoint.isBienSocola
                    || !Util.canDoWithTime(lastTimeBienSocola, 30000)) {
                return;
            }
            List<Player> playersMap = this.player.zone.getNotBosses();
            for (int i = playersMap.size() - 1; i >= 0; i--) {
                Player pl = playersMap.get(i);
                if (pl != null && pl.isPl() && !this.player.equals(pl) && !pl.isDie()
                        && Util.getDistance(this.player, pl) <= 200) {
                    int duration = this.player.nPoint.bienSocola30Seconds ? 30_000 : 6_000;
                    EffectSkillService.gI().setSocola(pl, System.currentTimeMillis(), duration);
                    Service.gI().Send_Caitrang(pl);
                    ItemTimeService.gI().sendItemTime(pl, 4133, duration / 1000);
                }
            }
            this.lastTimeBienSocola = System.currentTimeMillis();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Option 115: touching another player turns that player into a carrot. */
    private void updateBienCarrot() {
        try {
            if (this.player.nPoint == null || !this.player.nPoint.isBienCarrot
                    || !Util.canDoWithTime(lastTimeBienCarrot, 250)) {
                return;
            }
            for (Player pl : this.player.zone.getNotBosses()) {
                if (pl != null && pl.isPl() && !this.player.equals(pl) && !pl.isDie()
                        && pl.effectSkill != null && !pl.effectSkill.isCarrot
                        && Util.getDistance(this.player, pl) <= 40) {
                    EffectSkillService.gI().setCarrot(pl, 600_000);
                }
            }
            this.lastTimeBienCarrot = System.currentTimeMillis();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Option 163 mirrors the carrot contact mechanic with a pumpkin penalty. */
    private void updateBienPumpkin() {
        try {
            if (this.player.nPoint == null || !this.player.nPoint.isBienPumpkin
                    || !Util.canDoWithTime(lastTimeBienPumpkin, 250)) {
                return;
            }
            for (Player pl : this.player.zone.getNotBosses()) {
                if (pl != null && pl.isPl() && !this.player.equals(pl) && !pl.isDie()
                        && pl.effectSkill != null && !pl.effectSkill.isPumpkin
                        && Util.getDistance(this.player, pl) <= 40) {
                    EffectSkillService.gI().setPumpkin(pl, 600_000);
                }
            }
            this.lastTimeBienPumpkin = System.currentTimeMillis();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Option 162 restores KI every second to the wearer and nearby players. */
    private void updateCuteMpRecovery() {
        int percent = this.player.nPoint == null ? 0 : this.player.nPoint.cuteMpRecoveryPercent;
        if (percent <= 0 || !Util.canDoWithTime(lastTimeCuteMpRecovery, 1_000)) {
            return;
        }
        for (Player target : this.player.zone.getNotBosses()) {
            if (target == null || !target.isPl() || target.isDie() || target.nPoint == null
                    || Util.getDistance(this.player, target) > 200) {
                continue;
            }
            long recovery = (long) target.nPoint.mpMax * Math.min(100, percent) / 100L;
            if (recovery > 0 && target.nPoint.mp < target.nPoint.mpMax) {
                PlayerService.gI().hoiPhuc(target, 0, recovery);
            }
        }
        this.lastTimeCuteMpRecovery = System.currentTimeMillis();
    }

    /** Option 178 restores the configured percentage of max KI every 10 seconds. */
    private void updateOption178Recovery() {
        int percent = this.player.nPoint == null ? 0 : this.player.nPoint.mpRecovery10SecondsPercent;
        if (percent <= 0 || !Util.canDoWithTime(lastTimeOption178Recovery, 10_000)) {
            return;
        }
        long recovery = (long) this.player.nPoint.mpMax * Math.min(100, percent) / 100L;
        if (recovery > 0 && this.player.nPoint.mp < this.player.nPoint.mpMax) {
            PlayerService.gI().hoiPhuc(this.player, 0, recovery);
        }
        this.lastTimeOption178Recovery = System.currentTimeMillis();
    }

    /** Option 221 heals the wearer and members of the same clan in 200 range every 30 seconds. */
    private void updateOption221Recovery() {
        int percent = this.player.nPoint == null ? 0 : this.player.nPoint.clanRecoveryPercent;
        if (percent <= 0 || !Util.canDoWithTime(lastTimeOption221Recovery, 30_000)) {
            return;
        }
        recoverOption221Target(this.player, percent);
        if (this.player.clan != null) {
            for (Player target : this.player.zone.getNotBosses()) {
                if (target == null || target.equals(this.player) || !target.isPl() || target.isDie()
                        || target.clan != this.player.clan || Util.getDistance(this.player, target) > 200) {
                    continue;
                }
                recoverOption221Target(target, percent);
            }
        }
        lastTimeOption221Recovery = System.currentTimeMillis();
    }

    private void recoverOption221Target(Player target, int percent) {
        if (target == null || target.nPoint == null || target.isDie()) {
            return;
        }
        int safePercent = Math.min(100, Math.max(0, percent));
        PlayerService.gI().hoiPhuc(target,
                (long) target.nPoint.hpMax * safePercent / 100L,
                (long) target.nPoint.mpMax * safePercent / 100L);
    }

    /** Option 222 activates for 30 seconds once per 120-second cycle. */
    private void updateOption222Rage() {
        boolean hasOption = hasEquippedOption(this.player, 222);
        long now = System.currentTimeMillis();
        if (!hasOption) {
            option222ActiveUntil = 0L;
            lastTimeOption222Activation = 0L;
        } else if (option222ActiveUntil == 0L
                || now >= lastTimeOption222Activation + 120_000L) {
            lastTimeOption222Activation = now;
            option222ActiveUntil = now + 30_000L;
        }
        boolean isActive = isOption222Active();
        if (option222ActiveState != isActive && this.player.nPoint != null) {
            option222ActiveState = isActive;
            this.player.nPoint.calPoint();
            Service.gI().point(this.player);
            Service.gI().Send_Info_NV(this.player);
        }
    }

    public boolean isOption222Active() {
        return option222ActiveUntil > System.currentTimeMillis();
    }

    /** Option 223 restores the configured total HP over six five-second ticks. */
    private void updateOption223Recovery() {
        if (!hasEquippedOption(this.player, 223)) {
            option223ActiveUntil = 0L;
            lastTimeOption223Activation = 0L;
            option223HealTicks = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (option223ActiveUntil == 0L || now >= lastTimeOption223Activation + 120_000L) {
            lastTimeOption223Activation = now;
            option223ActiveUntil = now + 30_000L;
            // First recovery happens as the effect starts; the remaining five
            // ticks are spaced at five-second intervals within the 30 seconds.
            lastTimeOption223Heal = now - 5_000L;
            option223HealTicks = 0;
        }
        if (now >= option223ActiveUntil || option223HealTicks >= 6
                || !Util.canDoWithTime(lastTimeOption223Heal, 5_000)) {
            return;
        }
        int percent = Math.min(100, getEquippedOptionParam(this.player, 223));
        long totalRecovery = (long) this.player.nPoint.hpMax * percent / 100L;
        long recovery = option223HealTicks == 5
                ? totalRecovery - (totalRecovery / 6L) * 5L : totalRecovery / 6L;
        if (recovery > 0) {
            PlayerService.gI().hoiPhuc(this.player, recovery, 0);
        }
        option223HealTicks++;
        lastTimeOption223Heal = now;
    }

    /** Option 224 gives every skill the 0.1 second cooldown for 30 seconds, then rests for 120 seconds. */
    private void updateOption224CooldownRush() {
        if (!hasEquippedOption(this.player, 224)) {
            option224ActiveUntil = 0L;
            lastTimeOption224Activation = 0L;
            return;
        }
        long now = System.currentTimeMillis();
        if (option224ActiveUntil == 0L || now >= option224ActiveUntil + 120_000L) {
            lastTimeOption224Activation = now;
            option224ActiveUntil = now + 30_000L;
        }
    }

    public boolean isOption224Active() {
        return option224ActiveUntil > System.currentTimeMillis();
    }

    /** Option 229 damages only opponents in 200 range and never reduces them below 1 HP. */
    private void updateOption229ColdAura() {
        int percent = this.player.nPoint == null ? 0 : this.player.nPoint.coldAuraDrainPercent;
        if (percent <= 0 || !Util.canDoWithTime(lastTimeOption229Cold, 30_000)) {
            return;
        }
        int safePercent = Math.min(100, percent);
        for (Player target : this.player.zone.getNotBosses()) {
            if (target == null || target.equals(this.player) || !target.isPl() || target.isDie()
                    || target.nPoint == null || Util.getDistance(this.player, target) > 200
                    || !SkillService.gI().canAttackPlayer(this.player, target)) {
                continue;
            }
            int hpLoss = (int) ((long) target.nPoint.hpMax * safePercent / 100L);
            int mpLoss = (int) ((long) target.nPoint.mpMax * safePercent / 100L);
            target.nPoint.setHp(Math.max(1, target.nPoint.hp - hpLoss));
            target.nPoint.setMp(Math.max(0, target.nPoint.mp - mpLoss));
            PlayerService.gI().sendInfoHpMpMoney(target);
            Service.gI().Send_Info_NV(target);
        }
        lastTimeOption229Cold = System.currentTimeMillis();
    }

    private boolean hasEquippedOption(Player target, int optionId) {
        if (target == null || target.inventory == null || target.inventory.itemsBody == null) {
            return false;
        }
        for (Item item : target.inventory.itemsBody) {
            if (hasOption(item, optionId)) {
                return true;
            }
        }
        return false;
    }

    /** Keeps stateful options from surviving after their equipment is removed. */
    public void syncEquipmentOptionState(boolean hasAbsorbOption, int maxPunchBonus) {
        punchComboMaxPercent = Math.max(0, maxPunchBonus);
        if (punchComboMaxPercent == 0) {
            punchComboPercent = 0;
        } else if (punchComboPercent > punchComboMaxPercent) {
            punchComboPercent = punchComboMaxPercent;
        }
        if (!hasAbsorbOption) {
            absorbStackCount = 0;
            absorbedDamage = 0L;
            lastTimeAbsorbBurst = 0L;
        }
    }

    /** Option 156 increments only for the three race-specific basic punches. */
    public void recordAttackForPunchCombo(int skillTemplateId) {
        if (punchComboMaxPercent <= 0) {
            punchComboPercent = 0;
            return;
        }
        boolean isPunch = skillTemplateId == Skill.DRAGON
                || skillTemplateId == Skill.DEMON || skillTemplateId == Skill.GALICK;
        punchComboPercent = isPunch ? Math.min(punchComboMaxPercent, punchComboPercent + 1) : 0;
    }

    /** Option 168 delegates the roll and area burst to the central option service. */
    public boolean tryAbsorbEquipmentDamage(long damage) {
        return EquipmentOptionService.gI().tryAbsorbDamage(this.player, damage);
    }

    private void updateXChuong() {
        try {
            if (this.player.nPoint != null && this.player.nPoint.xChuong != 0) {
                if (Util.canDoWithTime(lastTimeXChuong, 60000)) {
                    this.isXChuong = true;
                    this.lastTimeXChuong = System.currentTimeMillis();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateMaPhongBa() {
        try {
            if (this.player.effectSkill != null && this.player.effectSkill.isBinh && this.player.effectSkill.playerUseMafuba != null) {
                if (Util.canDoWithTime(lastTimeMaPhongBa, 500) && this.player.effectSkill.playerUseMafuba.playerSkill != null) {
                    double param = this.player.effectSkill.playerUseMafuba.playerSkill.getSkillbyId(Skill.MA_PHONG_BA).point * (this.player.effectSkill.typeBinh == 0 ? 1 : 2);
                    int subHp = (int) ((long) this.player.effectSkill.playerUseMafuba.nPoint.hpMax * param / 100);
                    if (subHp >= this.player.nPoint.hp) {
                        subHp = Math.abs(this.player.nPoint.hp - 100);
                    }
                    PlayerService.gI().sendInfoHpMpMoney(this.player);
                    Service.gI().Send_Info_NV(this.player);
                    this.player.injured(this.player.effectSkill.playerUseMafuba, subHp, true, false);
                    this.lastTimeMaPhongBa = System.currentTimeMillis();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // private void updateHalloween() {
    //     try {
    //         if (this.player.effectSkill != null && this.player.effectSkill.isHalloween) {
    //             if (Util.canDoWithTime(lastTimeHalloween, 10000)) {
    //                 List<Player> playersMap = this.player.zone.getNotBosses();
    //                 for (int i = playersMap.size() - 1; i >= 0; i--) {
    //                     Player pl = playersMap.get(i);
    //                     if (pl != null && pl.nPoint != null && !this.player.equals(pl) && pl.effectSkill != null && !pl.effectSkill.isHalloween && !pl.isPet && !pl.isDie()
    //                             && Util.getDistance(this.player, pl) <= 200) {
    //                         EffectSkillService.gI().setIsHalloween(pl, -1, 1800000);
    //                     }
    //                 }
    //                 this.lastTimeHalloween = System.currentTimeMillis();
    //             }
    //         }
    //     } catch (Exception e) {
    //         e.printStackTrace();
    //     }
    // }
    //giáp tập luyện
    private void updateTrainArmor() {
        if (Util.canDoWithTime(lastTimeAddTimeTrainArmor, 60000) && !Util.canDoWithTime(lastTimeAttack, 30000)) {
            if (this.player.nPoint.wearingTrainArmor) {
                Item trainArmor = this.player.inventory.trainArmor;
                ItemService.gI().ensureTrainArmorTimeOption(trainArmor);
                int maxMinutes = ItemService.gI().getMaxTrainArmorMinutes(trainArmor);
                for (Item.ItemOption io : trainArmor.itemOptions) {
                    if (io.optionTemplate.id == 9) {
                        int oldMinutes = io.param;
                        if (io.param < maxMinutes) {
                            io.param++;
                        } else if (io.param > maxMinutes) {
                            io.param = maxMinutes;
                        }
                        if (io.param != oldMinutes) {
                            InventoryService.gI().sendItemBody(player);
                        }
                        break;
                    }
                }
            }
            this.lastTimeAddTimeTrainArmor = System.currentTimeMillis();
        }
        if (Util.canDoWithTime(lastTimeSubTimeTrainArmor, 60000)) {
            for (Item item : this.player.inventory.itemsBag) {
                if (item.isNotNullItem()) {
                    if (ItemService.gI().isTrainArmor(item)) {
                        for (Item.ItemOption io : item.itemOptions) {
                            if (io.optionTemplate.id == 9) {
                                if (io.param > 0) {
                                    io.param--;
                                }
                            }
                        }
                    }
                } else {
                    break;
                }
            }
            for (Item item : this.player.inventory.itemsBox) {
                if (item.isNotNullItem()) {
                    if (ItemService.gI().isTrainArmor(item)) {
                        for (Item.ItemOption io : item.itemOptions) {
                            if (io.optionTemplate.id == 9) {
                                if (io.param > 0) {
                                    io.param--;
                                }
                            }
                        }
                    }
                } else {
                    break;
                }
            }
            this.lastTimeSubTimeTrainArmor = System.currentTimeMillis();
            InventoryService.gI().sendItemBags(player);
            Service.gI().point(this.player);
        }
    }

    private void updateVoHinh() {
        if (this.player.nPoint != null && this.player.nPoint.wearingVoHinh) {
            // Option 105 hides the player from normal monster target
            // acquisition whenever they have not attacked a monster recently.
            // Player-versus-player combat must not cancel this protection.
            isVoHinh = Util.canDoWithTime(lastTimeAttackMob, 5000);
        } else {
            isVoHinh = false;
        }
    }

    public void dispose() {
        this.player = null;
    }
}
