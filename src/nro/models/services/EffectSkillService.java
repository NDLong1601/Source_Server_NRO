package nro.models.services;

import nro.models.mob.Mob;
import nro.models.player.Player;
import nro.models.skill.Skill;
import nro.models.network.Message;
import nro.models.consts.ConstPlayer;
import nro.models.map.service.MapService;
import nro.models.utils.SkillUtil;
import java.util.List;
import nro.models.utils.Util;
import nro.models.map.MaBuHold;

/**
 *
 * @author By Mr Blue
 * 
 */

public class EffectSkillService {

    public static final byte TURN_ON_EFFECT = 1;
    public static final byte TURN_OFF_EFFECT = 0;
    public static final byte TURN_OFF_ALL_EFFECT = 2;

    public static final byte HOLD_EFFECT = 32;
    public static final byte SHIELD_EFFECT = 33;
    public static final byte HUYT_SAO_EFFECT = 39;
    public static final byte BLIND_EFFECT = 40;
    public static final byte SLEEP_EFFECT = 41;
    public static final byte STONE_EFFECT = 42;

    private static EffectSkillService instance;

    private EffectSkillService() {

    }

    public static EffectSkillService gI() {
        if (instance == null) {
            instance = new EffectSkillService();
        }
        return instance;
    }

    public void sendEffectUseSkill(Player player, byte skillId) {
        Skill skill = SkillUtil.getSkillbyId(player, skillId);
        Message msg;
        try {
            msg = new Message(-45);
            msg.writer().writeByte(8);
            msg.writer().writeInt((int) player.id);
            msg.writer().writeShort(skill.skillId);
            Service.gI().sendMessAnotherNotMeInMap(player, msg);
            msg.cleanup();
        } catch (Exception e) {
            nro.models.utils.Logger.logException(EffectSkillService.class, e);
        }
    }

    public void sendEffectPlayer(Player plUseSkill, Player plTarget, byte toggle, byte effect) {
        Message msg;
        try {
            msg = new Message(-124);
            msg.writer().writeByte(toggle); //0: hủy hiệu ứng, 1: bắt đầu hiệu ứng
            msg.writer().writeByte(0); //0: vào phần phayer, 1: vào phần mob
            if (toggle == TURN_OFF_ALL_EFFECT) {
                msg.writer().writeInt((int) plTarget.id);
            } else {
                msg.writer().writeByte(effect); //loại hiệu ứng
                msg.writer().writeInt((int) plTarget.id); //id player dính effect
                msg.writer().writeInt((int) plUseSkill.id); //id player dùng skill
            }
            Service.gI().sendMessAllPlayerInMap(plUseSkill, msg);
            msg.cleanup();
        } catch (Exception e) {
            nro.models.utils.Logger.logException(EffectSkillService.class, e);
        }
    }

    public void sendEffectMob(Player plUseSkill, Mob mobTarget, byte toggle, byte effect) {
        Message msg;
        try {
            msg = new Message(-124);
            msg.writer().writeByte(toggle); //0: hủy hiệu ứng, 1: bắt đầu hiệu ứng
            msg.writer().writeByte(1); //0: vào phần phayer, 1: vào phần mob
            msg.writer().writeByte(effect); //loại hiệu ứng
            msg.writer().writeByte(mobTarget.id); //id mob dính effect
            msg.writer().writeInt((int) plUseSkill.id); //id player dùng skill
            Service.gI().sendMessAllPlayerInMap(mobTarget.zone, msg);
            msg.cleanup();
        } catch (Exception e) {
            nro.models.utils.Logger.logException(EffectSkillService.class, e);
        }
    }

    public void removeUseTroi(Player player) {
        if (player.effectSkill.mobAnTroi != null) {
            player.effectSkill.mobAnTroi.effectSkill.removeAnTroi();
        }
        if (player.effectSkill.plAnTroi != null) {
            removeAnTroi(player.effectSkill.plAnTroi);
        }
        player.effectSkill.useTroi = false;
        player.effectSkill.mobAnTroi = null;
        player.effectSkill.plAnTroi = null;
        sendEffectPlayer(player, player, TURN_OFF_EFFECT, HOLD_EFFECT);
    }

    public void removeAnTroi(Player player) {
        if (player != null && player.effectSkill != null) {
            player.effectSkill.anTroi = false;
            player.effectSkill.plTroi = null;
            sendEffectPlayer(player, player, TURN_OFF_EFFECT, HOLD_EFFECT);
        }
    }

    public void setAnTroi(Player player, Player plTroi, long lastTimeAnTroi, int timeAnTroi) {
        player.effectSkill.anTroi = true;
        player.effectSkill.plTroi = plTroi;
    }

    public void setUseTroi(Player player, long lastTimeTroi, int timeTroi) {
        player.effectSkill.useTroi = true;
        player.effectSkill.lastTimeTroi = lastTimeTroi;
        player.effectSkill.timeTroi = timeTroi;
    }

    public void setThoiMien(Player player, long lastTimeThoiMien, int timeThoiMien) {
        player.effectSkill.isThoiMien = true;
        player.effectSkill.lastTimeThoiMien = lastTimeThoiMien;
        player.effectSkill.timeThoiMien = timeThoiMien;
    }

    public void removeThoiMien(Player player) {
        player.effectSkill.isThoiMien = false;
        sendEffectPlayer(player, player, TURN_OFF_EFFECT, SLEEP_EFFECT);
    }

    public void startStun(Player player, long lastTimeStartBlind, int timeBlind) {
        player.effectSkill.lastTimeStartStun = lastTimeStartBlind;
        player.effectSkill.timeStun = timeBlind;
        player.effectSkill.isStun = true;
        sendEffectPlayer(player, player, TURN_ON_EFFECT, BLIND_EFFECT);
    }

    public void removeStun(Player player) {
        player.effectSkill.isStun = false;
        sendEffectPlayer(player, player, TURN_OFF_EFFECT, BLIND_EFFECT);
    }

    public void setSocola(Player player, long lastTimeSocola, int timeSocola) {
        player.effectSkill.lastTimeSocola = lastTimeSocola;
        player.effectSkill.timeSocola = timeSocola;
        player.effectSkill.isSocola = true;
        player.effectSkill.countPem1hp = 0;
    }

    public void removeSocola(Player player) {
        player.effectSkill.isSocola = false;
        Service.gI().Send_Caitrang(player);
    }

    public void sendMobToSocola(Player player, Mob mob, int timeSocola) {
        Message msg;
        try {
            msg = new Message(-112);
            msg.writer().writeByte(1);
            msg.writer().writeByte(mob.id); //mob id
            msg.writer().writeShort(4133); //icon socola
            Service.gI().sendMessAllPlayerInMap(player, msg);
            msg.cleanup();
            mob.effectSkill.setSocola(System.currentTimeMillis(), timeSocola);
        } catch (Exception e) {
            nro.models.utils.Logger.logException(EffectSkillService.class, e);
        }
    }

    public void setBlindDCTT(Player player, long lastTimeDCTT, int timeBlindDCTT) {
        player.effectSkill.isBlindDCTT = true;
        player.effectSkill.lastTimeBlindDCTT = lastTimeDCTT;
        player.effectSkill.timeBlindDCTT = timeBlindDCTT;
    }

    public void removeBlindDCTT(Player player) {
        player.effectSkill.isBlindDCTT = false;
        sendEffectPlayer(player, player, TURN_OFF_EFFECT, BLIND_EFFECT);
    }

    public void setStartHuytSao(Player player, int tiLeHP) {
        player.effectSkill.tiLeHPHuytSao = tiLeHP;
        player.effectSkill.lastTimeHuytSao = System.currentTimeMillis();
    }

    public void removeHuytSao(Player player) {
        player.effectSkill.tiLeHPHuytSao = 0;
        sendEffectPlayer(player, player, TURN_OFF_EFFECT, HUYT_SAO_EFFECT);
        Service.gI().point(player);
        Service.gI().Send_Info_NV(player);
    }

    public void startUseSkillMonkey(Player player) {
        sendEffectMonkey(player);
        if (player.isBoss) {
            setIsMonkey(player);
            return;
        }
        Service.gI().sendSpeedPlayer(player, 0);
        player.effectSkill.isUseSkillMonkey = true;
        player.effectSkill.timeUseSkillMonkey = 1500;
        player.effectSkill.lastTimeUseSkillMonkey = System.currentTimeMillis();
    }

    public void finishUseMonkey(Player player) {
        if (player.effectSkill != null) {
            player.effectSkill.isUseSkillMonkey = false;
            Service.gI().sendSpeedPlayer(player, -1);
            if (!player.isDie()) {
                setIsMonkey(player);
            }
        }
    }

    public void setIsMonkey(Player player) {
        EffectSkillService.gI().sendEffectMonkey(player);
        int timeMonkey = SkillUtil.getTimeMonkey(player.playerSkill.skillSelect.point);
        if (player.setClothes.cadic == 5) {
            timeMonkey *= 5;
        }
        player.effectSkill.isMonkey = true;
        player.effectSkill.timeMonkey = timeMonkey;
        player.effectSkill.lastTimeUpMonkey = System.currentTimeMillis();
        player.effectSkill.levelMonkey = (byte) player.playerSkill.skillSelect.point;
        player.nPoint.setHp(((int) player.nPoint.hp * 2));
        Service.gI().Send_Caitrang(player);
        if (!player.isPet) {
            PlayerService.gI().sendInfoHpMp(player);
        }
        Service.gI().point(player);
        Service.gI().Send_Info_NV(player);
        Service.gI().sendInfoPlayerEatPea(player);
    }

    public void monkeyDown(Player player) {
        player.effectSkill.isMonkey = false;
        player.effectSkill.levelMonkey = 0;
        if (player.nPoint.hp > player.nPoint.hpMax) {
            player.nPoint.setHp(player.nPoint.hpMax);
        }
        sendEffectEndCharge(player);
        sendEffectMonkey(player);
        Service.gI().setNotMonkey(player);
        Service.gI().Send_Caitrang(player);
        Service.gI().point(player);
        PlayerService.gI().sendInfoHpMp(player);
        Service.gI().Send_Info_NV(player);
        Service.gI().sendInfoPlayerEatPea(player);
    }

    public void setIntrinsic(Player player, int skillId, int cooldown, long lastTimeUseSkill) {
        player.effectSkill.isIntrinsic = true;
        player.effectSkill.skillID = skillId;
        player.effectSkill.cooldown = cooldown;
        player.effectSkill.lastTimeUseSkill = lastTimeUseSkill;
    }

    public void releaseCooldownSkillByIntrinsic(Player player) {
        player.effectSkill.isIntrinsic = false;
        Skill skill = SkillUtil.getSkillbyId(player, player.effectSkill.skillID);
        Service.gI().releaseCooldownSkill(player, skill);
    }

    public void setIsBinh(Player plAtt, Player player, int time) {
        if (player.effectSkill != null) {
            int typeBinh = plAtt.newSkill.typeItem;
            player.effectSkill.isBinh = true;
            player.effectSkill.typeBinh = typeBinh;
            player.effectSkill.timeBinh = time;
            player.effectSkill.playerUseMafuba = plAtt;
            player.effectSkill.lastTimeUpBinh = System.currentTimeMillis();
            ItemTimeService.gI().sendItemTime(player, typeBinh == 0 ? 11175 : 11166, time / 1000);
            Service.gI().Send_Caitrang(player);
        }
    }

    public void BinhDown(Player player) {
        if (player.effectSkill != null) {
            player.effectSkill.isBinh = false;
            Service.gI().Send_Caitrang(player);
        }
    }

    public void setIsHalloween(Player player, int outFit, int time) {
        if (player.effectSkill != null) {
            player.effectSkill.isHalloween = true;
            player.effectSkill.idOutfitHalloween = outFit != -1 ? outFit : Util.nextInt(5);
            player.effectSkill.timeHalloween = time;
            player.effectSkill.lastTimeHalloween = System.currentTimeMillis();
            ItemTimeService.gI().sendItemTime(player, 5101, time / 1000);
            Service.gI().Send_Caitrang(player);
        }
    }

    public void removeHalloween(Player player) {
        if (player.effectSkill != null) {
            player.effectSkill.isHalloween = false;
            Service.gI().Send_Caitrang(player);
        }
    }

    public void startUseMafuba(Player player, int time) {
        if (player.effectSkill != null) {
            player.effectSkill.isUseMafuba = true;
            player.effectSkill.timeUseMafuba = time;
            player.effectSkill.lastTimeUseMafuba = System.currentTimeMillis();
        }
    }

    public void finishUseMafuba(Player player) {
        if (player.effectSkill != null && player.newSkill != null && player.location != null) {
            player.effectSkill.isUseMafuba = false;
            for (Player playerMap : player.newSkill.playersTaget) {
                try {
                    if (player.location != null && playerMap.location != null) {
                        EffectSkillService.gI().setIsBinh(player, playerMap, 11000 * (player.newSkill.typeItem == 0 ? 1 : 2));
                        int x = player.location.x + ((player.newSkill.dir == -1) ? (-75) : 75);
                        int y = player.location.y;
                        if (player.zone != null && !MapService.gI().isMapBlackBallWar(player.zone.map.mapId)) {
                            Service.gI().setPos(playerMap, x, y);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            for (Mob mobMap : player.newSkill.mobsTaget) {
                mobMap.effectSkill.setBinh(player, System.currentTimeMillis(), 11000 * (player.effectSkill.typeBinh == 0 ? 1 : 2));
            }
        }
    }

    public void setIsStone(Player player, int time) {
        if (MapService.gI().isMapMaBu(player.zone.map.mapId)) {
            player.nPoint.hp /= 2;
            PlayerService.gI().sendInfoHp(player);
            Service.gI().Send_Info_NV(player);
        }
        player.effectSkill.isStone = true;
        player.effectSkill.timeStone = time;
        player.effectSkill.lastTimeStone = System.currentTimeMillis();
        ItemTimeService.gI().sendItemTime(player, 4392, time / 1000);
        Service.gI().Send_Caitrang(player);
        EffectSkillService.gI().sendEffectPlayer(player, player, EffectSkillService.TURN_ON_EFFECT, EffectSkillService.STONE_EFFECT);
    }

    public void removeStone(Player player) {
        player.effectSkill.isStone = false;
        Service.gI().Send_Caitrang(player);
        sendEffectPlayer(player, player, TURN_OFF_EFFECT, STONE_EFFECT);
    }

    public void setIsLamCham(Player player, int time) {
        player.nPoint.speed = 1;
        Service.gI().point(player);
        Service.gI().sendSpeedPlayer(player, -1);
        player.effectSkill.isLamCham = true;
        player.effectSkill.timeLamCham = time;
        player.effectSkill.lastTimeLamCham = System.currentTimeMillis();
    }

    public void removeLamCham(Player player) {
        player.nPoint.speed = 8;
        Service.gI().point(player);
        Service.gI().sendSpeedPlayer(player, -1);
        Service.gI().chat(player, "Nhẹ lại rồi!");
        player.effectSkill.isLamCham = false;
    }

    public void setIsTanHinh(Player player, int time) {
        Service.gI().setPos2(player, player.location.x, 10000);
        player.effectSkill.isTanHinh = true;
        player.effectSkill.timeTanHinh = time;
        player.effectSkill.lastTimeTanHinh = System.currentTimeMillis();
    }

    public void removeTanHinh(Player player) {
        Service.gI().setPos2(player, player.location.x, player.location.y);
        player.effectSkill.isTanHinh = false;
    }

    public void setDameBuff(Player player, int time, int tiLe) {
        player.effectSkill.isDameBuff = true;
        player.effectSkill.timeDameBuff = time;
        player.effectSkill.tileDameBuff = tiLe;
        player.effectSkill.lastTimeDameBuff = System.currentTimeMillis();
    }

    public void removeDameBuff(Player player) {
        player.effectSkill.isDameBuff = false;
    }

    public void setMabuHold(Player player, MaBuHold MabuHold) {
        short x = (short) MabuHold.x;
        short y = (short) MabuHold.y;
        player.maBuHold = MabuHold;
        player.precentMabuHold = 0;
        PlayerService.gI().changeAndSendTypePK(player, ConstPlayer.PK_ALL);
        Service.gI().sendMabuHold(player, 1, x, y);
        player.effectSkill.isMabuHold = true;
    }

    public void removeMabuHold(Player player) {
        player.maBuHold = null;
        player.precentMabuHold = 0;
        PlayerService.gI().changeAndSendTypePK(player, ConstPlayer.NON_PK);
        Service.gI().sendMabuHold(player, 0, (short) player.location.x, (short) 312);
        player.effectSkill.isMabuHold = false;
    }

    public void setPKCommeson(Player player, int time) {
        player.effectSkill.isPKCommeson = true;
        player.effectSkill.timePKCommeson = time;
        player.effectSkill.lastTimePKCommeson = System.currentTimeMillis();
        ItemTimeService.gI().sendItemTime(player, 2295, time / 1000);
    }

    public void removePKCommeson(Player player) {
        player.effectSkill.isPKCommeson = false;
        ItemTimeService.gI().sendItemTime(player, 2295, 0);
    }

    public void setPKSTT(Player player, int time) {
        player.effectSkill.isPKSTT = true;
        player.effectSkill.timePKSTT = time;
        player.effectSkill.lastTimePKSTT = System.currentTimeMillis();
        ItemTimeService.gI().sendItemTime(player, 2295, time / 1000);
    }

    public void removePKSTT(Player player) {
        player.effectSkill.isPKSTT = false;
        ItemTimeService.gI().sendItemTime(player, 2295, 0);
    }

    public void setChibi(Player player, int time) {
        player.effectSkill.isChibi = true;
        player.effectSkill.timeChibi = time;
        player.typeChibi = Util.nextInt(0, 3);
        if (player.typeChibi == 3) {
            player.nPoint.calPoint();
            player.nPoint.setHp((int) player.nPoint.hpMax);
            Service.gI().point(player);
            Service.gI().Send_Info_NV(player);
        }
        player.effectSkill.lastTimeChibi = System.currentTimeMillis();
        ItemTimeService.gI().sendItemTime(player, 433, time / 1000);
        Service.gI().sendChibi(player);
        Service.gI().sendHaveChibiFollowToAllMap(player);
    }

    public void removeChibi(Player player) {
        player.effectSkill.isChibi = false;
        player.typeChibi = -1;
        ItemTimeService.gI().sendItemTime(player, 433, 0);
        Service.gI().sendChibi(player);
        Service.gI().sendHaveChibiFollowToAllMap(player);
    }

    //**************************************************************************
    //Tái tạo năng lượng *******************************************************
    public void startCharge(Player player) {
        if (!player.effectSkill.isCharging) {
            player.effectSkill.isCharging = true;
            sendEffectCharge(player);
        }
    }

    public void stopCharge(Player player) {
        player.effectSkill.countCharging = 0;
        player.effectSkill.isCharging = false;;
        sendEffectStopCharge(player);

    }

    public void setStartShield(Player player) {
        player.effectSkill.isShielding = true;
        player.effectSkill.lastTimeShieldUp = System.currentTimeMillis();
        player.effectSkill.timeShield = SkillUtil.getTimeShield(player.playerSkill.skillSelect.point);
    }

    public void removeShield(Player player) {
        player.effectSkill.isShielding = false;
        sendEffectPlayer(player, player, TURN_OFF_EFFECT, SHIELD_EFFECT);
    }

    public void breakShield(Player player) {
        removeShield(player);
        Service.gI().sendThongBao(player, "Khiên năng lượng đã bị vỡ!");
        ItemTimeService.gI().removeItemTime(player, 3784);
    }

    public void sendEffectBlindThaiDuongHaSan(Player plUseSkill, List<Player> players, List<Mob> mobs, int timeStun) {
        Message msg;
        try {
            msg = new Message(-45);
            msg.writer().writeByte(0);
            msg.writer().writeInt((int) plUseSkill.id);
            msg.writer().writeShort(plUseSkill.playerSkill.skillSelect.skillId);
            msg.writer().writeByte(mobs.size());
            for (Mob mob : mobs) {
                msg.writer().writeByte(mob.id);
                msg.writer().writeByte(timeStun / 1000);
            }
            msg.writer().writeByte(players.size());
            for (Player pl : players) {
                msg.writer().writeInt((int) pl.id);
                msg.writer().writeByte(timeStun / 1000);
            }
            Service.gI().sendMessAllPlayerInMap(plUseSkill, msg);
            msg.cleanup();
        } catch (Exception e) {
            nro.models.utils.Logger.logException(EffectSkillService.class, e);
        }
    }

    public void sendEffectStartCharge(Player player) {
        Skill skill = SkillUtil.getSkillbyId(player, Skill.TAI_TAO_NANG_LUONG);
        Message msg;
        try {
            msg = new Message(-45);
            msg.writer().writeByte(6);
            msg.writer().writeInt((int) player.id);
            msg.writer().writeShort(skill.skillId);
            Service.gI().sendMessAllPlayerInMap(player, msg);
            msg.cleanup();
        } catch (Exception e) {
            nro.models.utils.Logger.logException(EffectSkillService.class, e);
        }
    }

    public void sendEffectCharge(Player player) {
        Skill skill = SkillUtil.getSkillbyId(player, Skill.TAI_TAO_NANG_LUONG);
        Message msg;
        try {
            msg = new Message(-45);
            msg.writer().writeByte(1);
            msg.writer().writeInt((int) player.id);
            msg.writer().writeShort(skill.skillId);
            Service.gI().sendMessAllPlayerInMap(player, msg);
            msg.cleanup();
        } catch (Exception e) {
            nro.models.utils.Logger.logException(EffectSkillService.class, e);
        }
    }

    public void sendEffectStopCharge(Player player) {
        try {
            Message msg = new Message(-45);
            msg.writer().writeByte(3);
            msg.writer().writeInt((int) player.id);
            msg.writer().writeShort(-1);
            Service.gI().sendMessAllPlayerInMap(player, msg);
            msg.cleanup();
        } catch (Exception e) {
            nro.models.utils.Logger.logException(EffectSkillService.class, e);
        }
    }

    public void sendEffectEndCharge(Player player) {
        Message msg;
        try {
            msg = new Message(-45);
            msg.writer().writeByte(5);
            msg.writer().writeInt((int) player.id);
            msg.writer().writeShort(player.playerSkill.skillSelect.skillId);
            Service.gI().sendMessAllPlayerInMap(player, msg);
            msg.cleanup();
        } catch (Exception e) {
            nro.models.utils.Logger.logException(EffectSkillService.class, e);
        }
    }

    public void sendEffectMonkey(Player player) {
        Skill skill = SkillUtil.getSkillbyId(player, Skill.BIEN_KHI);
        Message msg;
        try {
            msg = new Message(-45);
            msg.writer().writeByte(6);
            msg.writer().writeInt((int) player.id);
            msg.writer().writeShort(skill.skillId);
            Service.gI().sendMessAllPlayerInMap(player, msg);
            msg.cleanup();
        } catch (Exception e) {
            nro.models.utils.Logger.logException(EffectSkillService.class, e);
        }
    }

    public void setIsBodyChangeTechnique(Player player) {
        player.effectSkill.isBodyChangeTechnique = true;
        Service.gI().sendThongBao(player, "Bạn đã bị biến thành Tiểu Đội Trưởng");
        PlayerService.gI().changeAndSendTypePK(player, 5);
        for (int i = player.zone.getPlayers().size() - 1; i >= 0; i--) {
            Player pl = player.zone.getPlayers().get(i);
            Service.gI().playerInfoUpdate(player, pl, "!Tiểu đội trưởng", 180, 181, 182);
        }
    }

    public void removeBodyChangeTechnique(Player player) {
        PlayerService.gI().changeAndSendTypePK(player, 0);
        player.effectSkill.isTanHinh = false;
    }
}
