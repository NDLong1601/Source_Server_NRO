package nro.models.services;

import nro.models.consts.ConstPlayer;
import nro.models.boss.Broly.Broly;
import nro.models.boss.Broly.SuperBroly;
import nro.models.boss.sieu_hang.Rival;
import nro.models.boss.yardrat.Yardart;
import nro.models.consts.ConstAchievement;
import nro.models.intrinsic.Intrinsic;
import nro.models.mob.Mob;
import nro.models.mob.MobMe;
import nro.models.mob_bigboss.GauTuongCuop;
import nro.models.player.Pet;
import nro.models.player.Player;
import nro.models.player.NewSkill;
import nro.models.network.Message;
import java.io.IOException;
import nro.models.map.service.MapService;
import nro.models.utils.Logger;
import nro.models.utils.SkillUtil;
import nro.models.utils.Util;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import nro.models.boss.Boss;
import nro.models.boss.BossID;
import nro.models.npc.NonInteractiveNPC;
import nro.models.services_func.EffectMapService;
import nro.models.skill.Skill;
import nro.models.map.Zone;

public class SkillService {

    private static SkillService instance;
    private static final int KHI_NGUYEN_TRAM_MAX_TARGETS = 3;
    private static final int KHI_NGUYEN_TRAM_CHARGE_TIME = 240;
    private static final ScheduledExecutorService KHI_NGUYEN_TRAM_SCHEDULER
            = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "khi-nguyen-tram");
                thread.setDaemon(true);
                return thread;
            });

    private static final class KienzanCast {

        private final Player caster;
        private final Zone zone;
        private final Skill skill;
        private final int startX;
        private final int startY;
        private final int endX;
        private final int endY;
        private final byte direction;
        private final int verticalRange;

        private KienzanCast(Player caster, Skill skill, int startX, int startY,
                int endX, int endY, byte direction, int verticalRange) {
            this.caster = caster;
            this.zone = caster.zone;
            this.skill = skill;
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.direction = direction;
            this.verticalRange = verticalRange;
        }
    }

    private static final class KienzanTarget {

        private final Player player;
        private final Mob mob;
        private final int distance;

        private KienzanTarget(Player player, int distance) {
            this.player = player;
            this.mob = null;
            this.distance = distance;
        }

        private KienzanTarget(Mob mob, int distance) {
            this.player = null;
            this.mob = mob;
            this.distance = distance;
        }
    }

    public static SkillService gI() {
        if (instance == null) {
            instance = new SkillService();
        }
        return instance;
    }

    public boolean useSkill(Player player, Player plTarget, Mob mobTarget, int status, Message msg) {
        if (plTarget != null && player.clan != null && plTarget.clan != null && player.clan == plTarget.clan && MapService.gI().isMapBlackBallWar(plTarget.zone.map.mapId)) {
            Service.gI().chatJustForMe(player, plTarget, "Ê cùng bang mà");
            return false;
        }
        if (plTarget != null && (player.idNRNM != -1 || plTarget.idNRNM != -1) && player.clan != null && plTarget.clan != null && player.clan == plTarget.clan) {
            Service.gI().chatJustForMe(player, plTarget, "Ê cùng bang mà");
            return false;
        }
        if (plTarget != null && !Util.canDoWithTime(plTarget.lastTimeRevived, 1500)) {
            return false;
        }

        byte skillId = -1;
        Short dx = -1;
        Short dy = -1;
        byte dir = -1;
        Short x = -1;
        Short y = -1;
        int ghostCastId = -1;
        int ghostTargetType = 0;
        int ghostTargetId = -1;
        if (status == 20) {
            try {
                skillId = msg.reader().readByte();
                dx = msg.reader().readShort();
                dy = msg.reader().readShort();
                dir = msg.reader().readByte();
                x = msg.reader().readShort();
                y = msg.reader().readShort();
                if (skillId == Skill.SUPER_GHOST_KAMIKAZE) {
                    ghostCastId = msg.reader().readInt();
                    ghostTargetType = msg.reader().readUnsignedByte();
                    ghostTargetId = msg.reader().readInt();
                }
            } catch (IOException e) {
                if (skillId == Skill.SUPER_GHOST_KAMIKAZE) return false;
            }
        }
        if (player.effectSkill != null && player.effectSkill.isHaveEffectSkill()) {
            return false;
        }
        if (player.playerSkill == null) {
            return false;
        }
        // Siêu Ma Cảm Tử has two explicit commands on the normal special-skill
        // packet: the first creates the orbiting ghosts, the next one supplies
        // the player's chosen target.  Do this before the normal cooldown gate
        // because launching an already-created group costs neither KI nor a
        // second cooldown.
        if (status == 20 && skillId == Skill.SUPER_GHOST_KAMIKAZE) {
            if (player.playerSkill.skillSelect == null || player.playerSkill.skillSelect.template == null
                    || player.playerSkill.skillSelect.template.id != skillId) {
                selectSkill(player, skillId);
                return false;
            }
            if (CustomSkillService.gI().hasSuperGhostKamikaze(player)) {
                return CustomSkillService.gI().launchSuperGhostKamikaze(player,
                        ghostCastId, ghostTargetType, ghostTargetId);
            }
            // An order for an expired formation must never summon a fresh one.
            if (ghostCastId != 0) return false;
            if (!canUseSkillWithMana(player) || !canUseSkillWithCooldown(player)) {
                return false;
            }
            boolean started = CustomSkillService.gI().startSuperGhostKamikaze(player);
            if (started) {
                affterUseSkill(player, Skill.SUPER_GHOST_KAMIKAZE, false);
            }
            return started;
        }
        if (player.playerSkill.skillSelect.template.type == 2 && canUseSkillWithMana(player) && canUseSkillWithCooldown(player)) {
            useSkillBuffToPlayer(player, plTarget);
            return true;
        }
        if ((player.effectSkill != null && player.effectSkill.isHaveEffectSkill()
                && (player.playerSkill.skillSelect.template.id != Skill.TU_SAT
                && player.playerSkill.skillSelect.template.id != Skill.QUA_CAU_KENH_KHI
                && player.playerSkill.skillSelect.template.id != Skill.MAKANKOSAPPO))
                || (plTarget != null && !canAttackPlayer(player, plTarget))
                || (mobTarget != null && mobTarget.isDie())
                || !canUseSkillWithMana(player) || !canUseSkillWithCooldown(player)) {
            return false;
        }
        if (player.effectSkill != null && player.effectSkill.isHaveEffectSkill() && player.effectSkill.useTroi) {
            EffectSkillService.gI().removeUseTroi(player);
        }
        if (player.effectSkill != null && player.effectSkill.isCharging) {
            EffectSkillService.gI().stopCharge(player);
        }
        if (status == 20 && skillId != -1 && player.playerSkill.skillSelect.template.id != skillId) {
            selectSkill(player, skillId);
            return false;
        } else {
            switch (player.playerSkill.skillSelect.template.type) {
                case 1 -> {
                    if (!useSkillAttack(player, plTarget, mobTarget)) {
                        return false;
                    }
                }
                case 3 -> {
                    if (!useSkillAlone(player)) {
                        return false;
                    }
                }
                case 4 -> {
                    if (!useNewSkillNotFocus(player, plTarget, mobTarget, status, skillId, dx, dy, dir, x, y)) {
                        return false;
                    }
                }
                default -> {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean useNewSkillNotFocus(Player player, Player plTarget, Mob mobTarget, int status, byte skillId, Short dx, Short dy, byte dir, Short x, Short y) {
        try {
            if (skillId == -1 && (plTarget != null || mobTarget != null)) {
                skillId = player.playerSkill.skillSelect.template.id;
                dx = (short) player.location.x;
                dy = (short) player.location.y;
                if (plTarget != null) {
                    x = (short) plTarget.location.x;
                    y = (short) plTarget.location.y;
                } else {
                    x = (short) mobTarget.location.x;
                    y = (short) mobTarget.location.y;
                }
                dir = (byte) (dx > x ? -1 : 1);
            }
            boolean started = false;
            switch (skillId) {
                case Skill.SUPER_KAME, Skill.LIEN_HOAN_CHUONG, Skill.MA_PHONG_BA -> {
                    player.newSkill.setSkillSpecial(dir, dx, dy, x, y);
                    newSkillNotFocus(player, status);
                    started = true;
                    AchievementService.gI().checkDoneTask(player, ConstAchievement.TUYET_KY_THANH_THAO);
                }
                case Skill.KHI_NGUYEN_TRAM -> started = startKhiNguyenTram(player, x, y);
                case Skill.HELLZONE_GRENADE -> started = CustomSkillService.gI().startHellzoneGrenade(player, x, y);
                // Siêu Ma Cảm Tử is handled before the generic TYPE=4 branch so
                // a second click can launch an existing group during cooldown.
                case Skill.SUPER_GHOST_KAMIKAZE -> started = false;
            }
            if (started) {
                affterUseSkill(player, player.playerSkill.skillSelect.template.id,
                        skillId == Skill.KHI_NGUYEN_TRAM);
            }
            return started;
        } catch (Exception e) {
            Logger.logException(SkillService.class, e, "Không thể thi triển tuyệt kỹ đặc biệt");
            return false;
        }
    }

    /**
     * Khí Nguyên Trảm dùng status 20 để gồng, status 21 để phóng và kết thúc.
     * Client chạy VFX theo cùng thời gian bay mà server dùng để tính sát thương.
     * Toạ độ xuất phát luôn lấy từ server; hướng ngắm bị giới hạn bởi dx/dy.
     */
    private boolean startKhiNguyenTram(Player player, short requestedX, short requestedY) {
        if (player == null || player.zone == null || player.isDie()
                || player.playerSkill == null || player.playerSkill.skillSelect == null
                || requestedX < 0 || requestedY < 0) {
            return false;
        }
        Skill skill = player.playerSkill.skillSelect;
        int startX = player.location.x;
        int startY = player.location.y - 24;
        int horizontalRange = Math.max(160,
                SkillMasteryService.gI().applyRangeStat(skill, Math.max(160, skill.dx)));
        int verticalRange = Math.max(45,
                SkillMasteryService.gI().applyRangeStat(skill, Math.max(45, skill.dy)));
        int rawLength = requestedX - startX;
        byte direction = (byte) (rawLength < 0 ? -1 : 1);
        if (rawLength == 0) {
            direction = (byte) (player.location.x > requestedX ? -1 : 1);
        }
        int travelLength = Math.max(80, Math.min(horizontalRange, Math.abs(rawLength)));
        int endX = startX + direction * travelLength;
        int endY = Math.max(startY - verticalRange,
                Math.min(startY + verticalRange, requestedY - 24));
        int travelTime = Math.max(260, Math.min(620,
                260 + travelLength * 360 / Math.max(1, horizontalRange)));
        KienzanCast cast = new KienzanCast(player, skill, startX, startY, endX, endY,
                direction, verticalRange);
        sendKhiNguyenTram(cast, KHI_NGUYEN_TRAM_CHARGE_TIME);
        KHI_NGUYEN_TRAM_SCHEDULER.schedule(() -> {
            if (cast.caster.isDie() || cast.caster.zone != cast.zone) {
                return;
            }
            sendKhiNguyenTramFlight(cast, travelTime);
            KHI_NGUYEN_TRAM_SCHEDULER.schedule(() -> resolveKhiNguyenTram(cast),
                    travelTime, TimeUnit.MILLISECONDS);
        }, KHI_NGUYEN_TRAM_CHARGE_TIME, TimeUnit.MILLISECONDS);
        return true;
    }

    private void sendKhiNguyenTram(KienzanCast cast, int chargeTime) {
        Message message = null;
        try {
            message = new Message(-45);
            // Charge only. Status 21 below supplies the destination and releases
            // the client from stt == 0; HP packets alone cannot end this pose.
            message.writer().writeByte(20);
            message.writer().writeInt((int) cast.caster.id);
            message.writer().writeShort(Skill.KHI_NGUYEN_TRAM);
            message.writer().writeByte(4);
            message.writer().writeByte(cast.direction);
            message.writer().writeShort(chargeTime);
            message.writer().writeByte(0);
            message.writer().writeByte(0);
            message.writer().writeByte(0);
            Service.gI().sendMessAllPlayerInMap(cast.caster, message);
        } catch (IOException e) {
            Logger.logException(SkillService.class, e, "Không gửi được hiệu ứng Khí Nguyên Trảm");
        } finally {
            if (message != null) {
                message.cleanup();
            }
        }
    }

    private void sendKhiNguyenTramFlight(KienzanCast cast, int travelTime) {
        Message message = null;
        try {
            message = new Message(-45);
            message.writer().writeByte(21);
            message.writer().writeInt((int) cast.caster.id);
            message.writer().writeShort(Skill.KHI_NGUYEN_TRAM);
            message.writer().writeShort(cast.endX);
            message.writer().writeShort(cast.endY);
            message.writer().writeShort(travelTime);
            message.writer().writeShort(cast.verticalRange);
            message.writer().writeByte(0); // typePaint
            message.writer().writeByte(0); // No client-authoritative target list.
            message.writer().writeByte(0); // typeItem
            message.writer().writeByte(0); // level
            Service.gI().sendMessAllPlayerInMap(cast.caster, message);
        } catch (IOException e) {
            Logger.logException(SkillService.class, e, "Không gửi được pha bay Khí Nguyên Trảm");
        } finally {
            if (message != null) {
                message.cleanup();
            }
        }
    }

    private void resolveKhiNguyenTram(KienzanCast cast) {
        Player caster = cast.caster;
        if (caster == null || caster.isDie() || caster.zone != cast.zone
                || caster.playerSkill == null || cast.skill == null) {
            return;
        }
        List<KienzanTarget> targets = collectKhiNguyenTramTargets(cast);
        if (targets.isEmpty()) {
            return;
        }
        targets.sort(Comparator.comparingInt(target -> target.distance));
        synchronized (caster.playerSkill) {
            Skill previousSkill = caster.playerSkill.skillSelect;
            caster.playerSkill.skillSelect = cast.skill;
            try {
                int targetCount = Math.min(KHI_NGUYEN_TRAM_MAX_TARGETS, targets.size());
                for (int index = 0; index < targetCount; index++) {
                    KienzanTarget target = targets.get(index);
                    int damagePercent = 100 - index * 20;
                    if (target.player != null) {
                        playerAttackPlayer(caster, target.player, false, damagePercent);
                    } else if (target.mob != null) {
                        playerAttackMob(caster, target.mob, false, false, damagePercent, false);
                    }
                }
            } finally {
                caster.playerSkill.skillSelect = previousSkill;
            }
        }
    }

    private List<KienzanTarget> collectKhiNguyenTramTargets(KienzanCast cast) {
        List<KienzanTarget> targets = new ArrayList<>();
        Player caster = cast.caster;
        List<Player> players = caster.isBoss ? cast.zone.getNotBosses() : cast.zone.getHumanoids();
        for (Player target : new ArrayList<>(players)) {
            if (target == null || target == caster || target.zone != cast.zone || target.isDie()
                    || !canAttackPlayer(caster, target)
                    || !isOnKhiNguyenTramPath(cast, target.location.x, target.location.y - 24)) {
                continue;
            }
            targets.add(new KienzanTarget(target, distanceAlongKhiNguyenTram(cast, target.location.x)));
        }
        if (!caster.isBoss) {
            for (Mob target : new ArrayList<>(cast.zone.mobs)) {
                if (target == null || target.isDie() || target.zone != cast.zone
                        || !isOnKhiNguyenTramPath(cast, target.location.x, target.location.y - 18)) {
                    continue;
                }
                targets.add(new KienzanTarget(target, distanceAlongKhiNguyenTram(cast, target.location.x)));
            }
        }
        return targets;
    }

    private boolean isOnKhiNguyenTramPath(KienzanCast cast, int x, int y) {
        int distance = distanceAlongKhiNguyenTram(cast, x);
        int length = Math.max(1, Math.abs(cast.endX - cast.startX));
        if (distance < -28 || distance > length + 36) {
            return false;
        }
        int expectedY = cast.startY + (cast.endY - cast.startY)
                * Math.max(0, Math.min(length, distance)) / length;
        return Math.abs(y - expectedY) <= cast.verticalRange;
    }

    private int distanceAlongKhiNguyenTram(KienzanCast cast, int x) {
        return cast.direction == 1 ? x - cast.startX : cast.startX - x;
    }

    public void updateSkillSpecial(Player player) {
        try {
            if (player.newSkill == null || player.zone == null) {
                return;
            }
            if (player.isDie() || player.effectSkill.isHaveEffectSkill()) {
                player.newSkill.closeSkillSpecial();
                return;
            }
            if (player.newSkill.skillSelect.template.id == Skill.MA_PHONG_BA) {
                if (Util.canDoWithTime(player.newSkill.lastTimeSkillSpecial, NewSkill.TIME_GONG)) {
                    if (Util.isTrue(1, 50) && player.isPl()) {
                        Service.gI().sendThongBao(player, "Bạn đã kiệt sức vì dùng ma phong ba quá nhiều!");
                        player.setDie();
                    }
                    player.newSkill.lastTimeSkillSpecial = System.currentTimeMillis();
                    player.newSkill.closeSkillSpecial();
                    List<Player> playersMap;
                    if (player.isBoss) {
                        playersMap = player.zone.getNotBosses();
                    } else {
                        playersMap = player.zone.getHumanoids();
                    }

                    for (Player playerMap : playersMap) {
                        if (playerMap == null || playerMap.id == player.id) {
                            continue;
                        }
                        if (player.newSkill.dir == -1 && !playerMap.isDie() && Util.getDistance(player, playerMap) <= 500 && this.canAttackPlayer(player, playerMap)) {
                            player.newSkill.playersTaget.add(playerMap);

                        } else if (player.newSkill.dir == 1 && !playerMap.isDie() && Util.getDistance(player, playerMap) <= 500 && this.canAttackPlayer(player, playerMap)) {
                            player.newSkill.playersTaget.add(playerMap);
                        }
                    }

                    if (!player.isBoss) {
                        for (Mob mobMap : player.zone.mobs) {
                            if (mobMap == null) {
                                continue;
                            }
                            if (player.newSkill.dir == -1 && !mobMap.isDie() && Util.getDistance(player, mobMap) <= 500) {
                                player.newSkill.mobsTaget.add(mobMap);
                                mobMap.addTemporaryEnemies(player);
                            } else if (player.newSkill.dir == 1 && !mobMap.isDie() && Util.getDistance(player, mobMap) <= 500) {
                                player.newSkill.mobsTaget.add(mobMap);
                                mobMap.addTemporaryEnemies(player);
                            }
                        }
                    }
                    newSkillNotFocus(player, 21);
                    EffectSkillService.gI().startUseMafuba(player,
                            SkillMasteryService.gI().applyEffectStat(player.newSkill.skillSelect, 4000));
                    SkillMasteryService.gI().recordValidUse(player, player.newSkill.skillSelect);
                }
            } else {
                if (player.newSkill.stepSkillSpecial == 0 && Util.canDoWithTime(player.newSkill.lastTimeSkillSpecial, NewSkill.TIME_GONG)) {
                    player.newSkill.lastTimeSkillSpecial = System.currentTimeMillis();
                    player.newSkill.stepSkillSpecial = 1;
                    newSkillNotFocus(player, 21);
                } else if (player.newSkill.stepSkillSpecial == 1 && !Util.canDoWithTime(player.newSkill.lastTimeSkillSpecial, NewSkill.TIME_GONG)) {
                    List<Player> playersMap;
                    if (player.isBoss) {
                        playersMap = player.zone.getNotBosses();
                    } else {
                        playersMap = player.zone.getHumanoids();
                    }

                    for (Player playerMap : playersMap) {
                        if (playerMap == null || playerMap.id == player.id) {
                            continue;
                        }
                        if (player.newSkill.dir == -1 && player.location.x > playerMap.location.x && !playerMap.isDie()
                                && Math.abs(playerMap.location.x - player.newSkill._xPlayer) <= player.newSkill._xObjTaget
                                && Math.abs(playerMap.location.y - player.newSkill._yPlayer) <= player.newSkill._yObjTaget
                                && this.canAttackPlayer(player, playerMap)) {
                            this.playerAttackPlayer(player, playerMap, false);
                        }
                        if (player.newSkill.dir == 1 && player.location.x < playerMap.location.x && !playerMap.isDie()
                                && Math.abs(playerMap.location.x - player.newSkill._xPlayer) <= player.newSkill._xObjTaget
                                && Math.abs(playerMap.location.y - player.newSkill._yPlayer) <= player.newSkill._yObjTaget
                                && this.canAttackPlayer(player, playerMap)) {
                            this.playerAttackPlayer(player, playerMap, false);
                        }
                    }
                    if (!player.isBoss) {
                        for (Mob mobMap : player.zone.mobs) {
                            if (mobMap == null) {
                                continue;
                            }
                            if (player.newSkill.dir == -1 && player.location.x > mobMap.location.x && !mobMap.isDie()
                                    && Math.abs(mobMap.location.x - player.newSkill._xPlayer) <= player.newSkill._xObjTaget
                                    && Math.abs(mobMap.location.y - player.newSkill._yPlayer) <= player.newSkill._yObjTaget) {
                                this.playerAttackMob(player, mobMap, false, false);
                            }
                            if (player.newSkill.dir == 1 && player.location.x < mobMap.location.x && !mobMap.isDie()
                                    && Math.abs(mobMap.location.x - player.newSkill._xPlayer) <= player.newSkill._xObjTaget
                                    && Math.abs(mobMap.location.y - player.newSkill._yPlayer) <= player.newSkill._yObjTaget) {
                                this.playerAttackMob(player, mobMap, false, false);
                            }
                        }
                    }
                } else if (player.newSkill.stepSkillSpecial == 1) {
                    SkillMasteryService.gI().recordValidUse(player, player.newSkill.skillSelect);
                    player.newSkill.closeSkillSpecial();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendCurrLevelSpecial(Player player, Skill skill) {
        if (player == null || player.getSession() == null || player.getSession().version < 220) {
            return;
        }
        Message message = null;
        try {
            message = Service.gI().messageSubCommand((byte) 62);
            message.writer().writeShort(skill.skillId);
            message.writer().writeByte(0);
            message.writer().writeShort(skill.currLevel);
            // Client gốc chỉ nhận currLevel ở nhánh này. Bản client mastery
            // đọc thêm cấp thật và bộ chỉ số hiệu lực để không còn hiển thị
            // cấp/chỉ số tĩnh của skill cấp 7 sau khi tự lên cấp.
            message.writer().writeShort(Math.min(Short.MAX_VALUE, skill.getActualLevel()));
            message.writer().writeShort(Math.max(0, Math.min(Short.MAX_VALUE, skill.damage)));
            message.writer().writeInt(skill.manaUse);
            message.writer().writeInt(skill.coolDown);
            message.writer().writeInt(skill.dx);
            message.writer().writeInt(skill.dy);
            message.writer().writeInt(skill.maxFight);
            player.sendMessage(message);
        } catch (final IOException ex) {
        } finally {
            if (message != null) {
                message.cleanup();
            }
        }
    }

    // _______________________________NEW_SKILL_NOT_FOCUS_______________________________
    public void newSkillNotFocus(Player player, int status) {
        Message msg = null;
        try {
            NewSkill newSkill = player.newSkill;
            msg = new Message(-45);
            msg.writer().writeByte(status);
            msg.writer().writeInt((int) player.id);
            msg.writer().writeShort(newSkill.skillSelect.template.id);
            if (status == 20) {
                byte typeFrame = 4;
                switch (newSkill.skillSelect.template.id) {
                    case Skill.SUPER_KAME ->
                        typeFrame = 1;
                    case Skill.LIEN_HOAN_CHUONG ->
                        typeFrame = 2;
                    case Skill.MA_PHONG_BA ->
                        typeFrame = 3;
                }
                byte dir = newSkill.dir;
                short timeGong = NewSkill.TIME_GONG;
                boolean isFly = false;
                byte typePaint = newSkill.typePaint;
                byte typeItem = newSkill.typeItem;
                msg.writer().writeByte(typeFrame);
                msg.writer().writeByte(dir);
                msg.writer().writeShort(timeGong);
                msg.writer().writeByte((byte) (isFly ? 1 : 0));
                msg.writer().writeByte(typePaint);
                msg.writer().writeByte(typeItem);
            } else if (status == 21) {
                short pointX = (short) (newSkill._xPlayer + ((newSkill.dir == -1) ? (-newSkill._xObjTaget) : newSkill._xObjTaget));
                short pointY = (short) newSkill._yPlayer;
                short timeDame = NewSkill.TIME_GONG;
                short rangeDame = newSkill._yObjTaget;
                byte typePaint = newSkill.getTypePaint();
                byte typeItem = newSkill.getTypeItem();
                byte num = (byte) (player.newSkill.playersTaget.size() + player.newSkill.mobsTaget.size());
                msg.writer().writeShort(pointX);
                msg.writer().writeShort(pointY);
                msg.writer().writeShort(timeDame);
                msg.writer().writeShort(rangeDame);
                msg.writer().writeByte(typePaint);
                msg.writer().writeByte(num);
                if (num > 0) {
                    for (Player playerMap : player.newSkill.playersTaget) {
                        msg.writer().writeByte(1);
                        msg.writer().writeInt((int) playerMap.id);
                    }
                    for (Mob mobMap : player.newSkill.mobsTaget) {
                        msg.writer().writeByte(0);
                        msg.writer().writeByte(mobMap.id);
                    }
                }
                msg.writer().writeByte(typeItem);
            }
            Service.gI().sendMessAllPlayerInMap(player, msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void learSkillSpecial(Player player, byte skillID) {
        Message message = null;
        try {
            Skill curSkill = SkillUtil.createSkill(skillID, 1);
            SkillUtil.setSkill(player, curSkill);
            message = Service.gI().messageSubCommand((byte) 23);
            message.writer().writeShort(curSkill.skillId);
            player.sendMessage(message);
            message.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (message != null) {
                message.cleanup();
                message = null;
            }

        }
    }

    public boolean useSkillAttack(Player player, Player plTarget, Mob mobTarget) {
        if (player == null || player.playerSkill == null || player.playerSkill.skillSelect == null) {
            return false;
        }
        if (player.playerSkill.skillSelect.template.id == Skill.BARRIER_PRISON) {
            return CustomSkillService.gI().castBarrierPrison(player, plTarget, mobTarget);
        }
        if (player.effectSkill != null && player.effectSkill.useTroi) {
            EffectSkillService.gI().removeUseTroi(player);
        }
        if (!player.isBoss) {
            if (player.isPet) {
                if (player.nPoint.stamina > 0) {
                    player.nPoint.numAttack++;
                    boolean haveCharmPet = ((Pet) player).master.charms != null && ((Pet) player).master.charms.tdDeTu > System.currentTimeMillis();
                    if (haveCharmPet ? player.nPoint.numAttack >= 5 : player.nPoint.numAttack >= 2) {
                        player.nPoint.numAttack = 0;
                        player.nPoint.stamina--;
                    }
                } else {
                    ((Pet) player).askPea();
                    return false;
                }
            } else {
                if (player.nPoint.stamina > 0) {
                    if (player.charms.tdDeoDai < System.currentTimeMillis()) {
                        player.nPoint.numAttack++;
                        if (player.nPoint.numAttack == 500) {
                            player.nPoint.numAttack = 0;
                            player.nPoint.stamina--;
                            PlayerService.gI().sendCurrentStamina(player);
                        }
                    }
                } else {
                    Service.gI().sendThongBao(player, "Thể lực đã cạn kiệt, hãy nghỉ ngơi để lấy lại sức");
                    return false;
                }
            }
        }
        List<Mob> mobs;
        boolean miss = false;
        if (player.playerSkill.skillSelect.template.id == Skill.KAMEJOKO || player.playerSkill.skillSelect.template.id == Skill.MASENKO || player.playerSkill.skillSelect.template.id == Skill.ANTOMIC) {
            if (!player.isBoss && !player.isBot && !player.isPet) {
            }
        }
        switch (player.playerSkill.skillSelect.template.id) {
            case Skill.KAIOKEN:
                int hpUse = player.nPoint.hpMax / 100 * 10;
                if (player.setClothes.thanVuTruKaio >= 4) {
                    hpUse = player.nPoint.hpMax / 100 * 5;
                }
                if (player.nPoint.hp <= hpUse) {
                    break;
                } else {
                    Service.gI().sendEffAllPlayer(player, 1027, 1, -1, 20);
                    player.nPoint.setHp(player.nPoint.hp - hpUse);
                    PlayerService.gI().sendInfoHpMpMoney(player);
                    Service.gI().Send_Info_NV(player);
                }
            case Skill.DRAGON:
            case Skill.DEMON:
            case Skill.GALICK:
            case Skill.LIEN_HOAN:
                if (player.zone != null && player.zone.map.mapId != 113 && plTarget != null && Util.getDistance(player, plTarget) > Skill.RANGE_ATTACK_CHIEU_DAM) {
                    miss = true;
                }
                if (mobTarget != null && Util.getDistance(player, mobTarget) > Skill.RANGE_ATTACK_CHIEU_DAM) {
                    miss = true;
                }
            case Skill.KAMEJOKO:
            case Skill.MASENKO:
            case Skill.ANTOMIC:
                if (plTarget != null) {
                    playerAttackPlayer(player, plTarget, miss);
                }
                if (mobTarget != null) {
                    playerAttackMob(player, mobTarget, miss, false);
                }
                if (player.mobMe != null) {
                    player.mobMe.attack(plTarget, mobTarget, miss);
                }
                if (player.playerSkill != null
                        && player.playerSkill.skillSelect != null
                        && player.playerSkill.skillSelect.template != null) {
                    affterUseSkill(player, player.playerSkill.skillSelect.template.id,
                            !miss && (plTarget != null || mobTarget != null));
                }
                break;
            //******************************************************************
            case Skill.QUA_CAU_KENH_KHI:
                if (!player.playerSkill.prepareQCKK) {
                    //bắt đầu tụ quả cầu
                    player.playerSkill.prepareQCKK = true;
                    player.playerSkill.lastTimePrepareQCKK = System.currentTimeMillis();
                    sendPlayerPrepareSkill(player, 4000);
                } else {
                    //ném cầu
                    player.playerSkill.prepareQCKK = false;
                    mobs = new ArrayList<>();
                    if (plTarget != null) {
                        playerAttackPlayer(player, plTarget, false);
                        if (!player.isBoss) {
                            for (Mob mob : player.zone.mobs) {
                                if (!mob.isDie()
                                        && Util.getDistance(plTarget, mob) <= SkillUtil.getRangeQCKK(player.playerSkill.skillSelect)) {
                                    mobs.add(mob);
                                }
                            }
                        }
                    }
                    if (mobTarget != null) {
                        if (!player.isBoss) {
                            playerAttackMob(player, mobTarget, false, true);
                            for (Mob mob : player.zone.mobs) {
                                if (!mob.equals(mobTarget) && !mob.isDie()
                                        && Util.getDistance(mob, mobTarget) <= SkillUtil.getRangeQCKK(player.playerSkill.skillSelect)) {
                                    mobs.add(mob);
                                }
                            }
                        }
                    }
                    for (Mob mob : mobs) {
                        mob.injured(player, player.nPoint.getDameAttack(true), true);
                    }
                    PlayerService.gI().sendInfoHpMpMoney(player);
                    affterUseSkill(player, player.playerSkill.skillSelect.template.id,
                            plTarget != null || mobTarget != null);
                }
                break;
            case Skill.MAKANKOSAPPO:
                if (!player.playerSkill.prepareLaze) {
                    //bắt đầu nạp laze
                    player.playerSkill.prepareLaze = true;
                    player.playerSkill.lastTimePrepareLaze = System.currentTimeMillis();
                    sendPlayerPrepareSkill(player, 3000);
                } else {
                    //bắn laze
                    player.playerSkill.prepareLaze = false;
                    if (plTarget != null) {
                        playerAttackPlayer(player, plTarget, false);
                    }
                    if (mobTarget != null) {
                        playerAttackMob(player, mobTarget, false, true);
                    }
                    affterUseSkill(player, player.playerSkill.skillSelect.template.id,
                            plTarget != null || mobTarget != null);
                }
                PlayerService.gI().sendInfoHpMpMoney(player);
                break;
            case Skill.SOCOLA:
                EffectSkillService.gI().sendEffectUseSkill(player, Skill.SOCOLA);
                int timeSocola = SkillUtil.getTimeSocola(player.playerSkill.skillSelect);
                if (plTarget != null) {
                    int socolaWeakPercent = SkillUtil.getSocolaDamageReductionPercent(player.playerSkill.skillSelect);
                    EffectSkillService.gI().setSocola(plTarget, System.currentTimeMillis(), timeSocola, socolaWeakPercent);
                    Service.gI().Send_Caitrang(plTarget);
                    ItemTimeService.gI().sendItemTime(plTarget, 4133, timeSocola / 1000);
                }
                if (mobTarget != null) {
                    EffectSkillService.gI().sendMobToSocola(player, mobTarget, timeSocola);
                }
                affterUseSkill(player, player.playerSkill.skillSelect.template.id,
                        plTarget != null || mobTarget != null);
                break;
            case Skill.DICH_CHUYEN_TUC_THOI:
                int timeChoangDCTT = SkillUtil.getTimeDCTT(player.playerSkill.skillSelect);
                if (plTarget != null) {
                    if (player.isBoss) {
                        Service.gI().chat(player, "Dịch chuyển tức thời");
                    }
                    Service.gI().setPos(player, plTarget.location.x, plTarget.location.y);
                    playerAttackPlayer(player, plTarget, miss);
                    int actualBlindDuration = EffectSkillService.gI().setBlindDCTT(plTarget,
                            System.currentTimeMillis(), timeChoangDCTT);
                    if (actualBlindDuration > 0) {
                        EffectSkillService.gI().sendEffectPlayer(player, plTarget,
                                EffectSkillService.TURN_ON_EFFECT, EffectSkillService.BLIND_EFFECT);
                        ItemTimeService.gI().sendItemTime(plTarget, 3779, actualBlindDuration / 1000);
                    }
                    PlayerService.gI().sendInfoHpMpMoney(plTarget);
                }
                if (mobTarget != null) {
                    Service.gI().setPos(player, mobTarget.location.x, mobTarget.location.y);
                    playerAttackMob(player, mobTarget, false, false);
                    mobTarget.effectSkill.setStartBlindDCTT(System.currentTimeMillis(), timeChoangDCTT);
                    EffectSkillService.gI().sendEffectMob(player, mobTarget, EffectSkillService.TURN_ON_EFFECT, EffectSkillService.BLIND_EFFECT);
                }
                player.nPoint.isCrit100 = true;
                affterUseSkill(player, player.playerSkill.skillSelect.template.id,
                        plTarget != null || mobTarget != null);
                break;
            case Skill.THOI_MIEN:
                EffectSkillService.gI().sendEffectUseSkill(player, Skill.THOI_MIEN);
                int timeSleep = SkillUtil.getTimeThoiMien(player.playerSkill.skillSelect);
                if (plTarget != null) {
                    int weakAfterSleepPercent = SkillUtil.getWeakAfterSleepPercent(player.playerSkill.skillSelect);
                    EffectSkillService.gI().setThoiMien(plTarget, System.currentTimeMillis(), timeSleep, weakAfterSleepPercent);
                    EffectSkillService.gI().sendEffectPlayer(player, plTarget, EffectSkillService.TURN_ON_EFFECT, EffectSkillService.SLEEP_EFFECT);
                    ItemTimeService.gI().sendItemTime(plTarget, 3782, timeSleep / 1000);
                }
                if (mobTarget != null) {
                    mobTarget.effectSkill.setThoiMien(System.currentTimeMillis(), timeSleep);
                    EffectSkillService.gI().sendEffectMob(player, mobTarget, EffectSkillService.TURN_ON_EFFECT, EffectSkillService.SLEEP_EFFECT);
                }
                affterUseSkill(player, player.playerSkill.skillSelect.template.id,
                        plTarget != null || mobTarget != null);
                break;
            case Skill.TROI:
                EffectSkillService.gI().sendEffectUseSkill(player, Skill.TROI);
                int timeHold = SkillUtil.getTimeTroi(player.playerSkill.skillSelect);
                if (plTarget instanceof Boss && ((Boss) plTarget).id == BossID.BABY) {
                    timeHold = 5000;
                }
                if (mobTarget instanceof GauTuongCuop) {
                    timeHold = 5000;
                }
                EffectSkillService.gI().setUseTroi(player, System.currentTimeMillis(), timeHold);
                if (plTarget != null && (!plTarget.playerSkill.prepareQCKK && !plTarget.playerSkill.prepareLaze && !plTarget.playerSkill.prepareTuSat)) {
                    player.effectSkill.plAnTroi = plTarget;
                    EffectSkillService.gI().sendEffectPlayer(player, plTarget, EffectSkillService.TURN_ON_EFFECT, EffectSkillService.HOLD_EFFECT);
                    EffectSkillService.gI().setAnTroi(plTarget, player, System.currentTimeMillis(), timeHold);
                }
                if (mobTarget != null) {
                    player.effectSkill.mobAnTroi = mobTarget;
                    EffectSkillService.gI().sendEffectMob(player, mobTarget, EffectSkillService.TURN_ON_EFFECT, EffectSkillService.HOLD_EFFECT);
                    mobTarget.effectSkill.setTroi(System.currentTimeMillis(), timeHold);
                }
                affterUseSkill(player, player.playerSkill.skillSelect.template.id,
                        plTarget != null || mobTarget != null);
                break;
        }
        if (!player.isBoss) {
            if (player.playerSkill != null && player.playerSkill.skillSelect != null && player.playerSkill.skillSelect.template != null) {
                int skillId = player.playerSkill.skillSelect.template.id;
                switch (skillId) {
                    case Skill.KAMEJOKO:
                    case Skill.MASENKO:
                    case Skill.ANTOMIC:
                    case Skill.DRAGON:
                    case Skill.DEMON:
                    case Skill.GALICK:
                    case Skill.LIEN_HOAN:
                    case Skill.KAIOKEN:
                    case Skill.QUA_CAU_KENH_KHI:
                    case Skill.MAKANKOSAPPO:
                    case Skill.DICH_CHUYEN_TUC_THOI:
                        player.effectSkin.lastTimeAttack = System.currentTimeMillis();
                        break;
                }
                AchievementService.gI().checkDoneTaskUseSkill(player);
                player.doesNotAttack = false;
                player.lastTimePlayerNotAttack = System.currentTimeMillis();
            } else {
            }
        }
        return true;
    }

    public boolean useSkillAlone(Player player) {
        if (player == null || player.playerSkill == null || player.playerSkill.skillSelect == null) {
            return false;
        }
        if (player.playerSkill.skillSelect.template.id == Skill.ENERGY_ABSORPTION) {
            return CustomSkillService.gI().activateEnergyAbsorption(player);
        }
        List<Mob> mobs;
        List<Player> players;
        switch (player.playerSkill.skillSelect.template.id) {
            case Skill.THAI_DUONG_HA_SAN:
                int timeStun = SkillUtil.getTimeStun(player.playerSkill.skillSelect);
                if (player.setClothes.thienXinHang == 5) {
                    timeStun *= 2;
                }
                mobs = new ArrayList<>();
                players = new ArrayList<>();
                if (!MapService.gI().isMapOffline(player.zone.map.mapId)) {
                    List<Player> playersMap;
                    if (player.isBoss) {
                        playersMap = player.zone.getNotBosses();
                    } else {
                        playersMap = player.zone.getHumanoids();
                    }
                    for (Player pl : playersMap) {
                        if (pl != null && !player.equals(pl) && pl.nPoint != null && !pl.nPoint.khangTDHS) {
                            if (Util.getDistance(player, pl) <= SkillUtil.getRangeStun(player.playerSkill.skillSelect)
                                    && canAttackPlayer(player, pl)) {
                                if (player.isPet && ((Pet) player).master.equals(pl)) {
                                    continue;
                                }
                                String[] text = {"Mắt của ta", "Chói mắt quá", "Đui mắt rồi", "Mù mắt rồi"};
                                Service.gI().chat(pl, text[Util.nextInt(text.length)]);
                                int actualTimeStun = timeStun;
                                if (pl.nPoint.tdhsDurationReductionPercent > 0) {
                                    int reduction = Math.min(100, pl.nPoint.tdhsDurationReductionPercent);
                                    actualTimeStun = Math.max(0, timeStun * (100 - reduction) / 100);
                                }
                                if (actualTimeStun > 0) {
                                    EffectSkillService.gI().startStun(pl, System.currentTimeMillis(), actualTimeStun);
                                    players.add(pl);
                                }
                            }
                        }
                    }
                }
                if (!player.isBoss) {
                    for (Mob mob : player.zone.mobs) {
                        if (Util.getDistance(player, mob) <= SkillUtil.getRangeStun(player.playerSkill.skillSelect)) {
                            mob.effectSkill.startStun(System.currentTimeMillis(), timeStun);
                            mobs.add(mob);
                        }
                    }
                }
                EffectSkillService.gI().sendEffectBlindThaiDuongHaSan(player, players, mobs, timeStun);
                affterUseSkill(player, player.playerSkill.skillSelect.template.id);
                break;
            case Skill.DE_TRUNG:
                EffectSkillService.gI().sendEffectUseSkill(player, Skill.DE_TRUNG);
                if (player.mobMe != null) {
                    player.mobMe.mobMeDie();
                    player.mobMe.dispose();
                    player.mobMe = null;
                }
                player.mobMe = new MobMe(player);
                affterUseSkill(player, player.playerSkill.skillSelect.template.id);
                break;
            case Skill.BIEN_KHI:
                EffectSkillService.gI().startUseSkillMonkey(player);
                affterUseSkill(player, player.playerSkill.skillSelect.template.id);
                break;
            case Skill.KHIEN_NANG_LUONG:
                EffectSkillService.gI().setStartShield(player);
                EffectSkillService.gI().sendEffectPlayer(player, player, EffectSkillService.TURN_ON_EFFECT, EffectSkillService.SHIELD_EFFECT);
                ItemTimeService.gI().sendItemTime(player, 3784, player.effectSkill.timeShield / 1000);
                affterUseSkill(player, player.playerSkill.skillSelect.template.id);
                break;
            case Skill.HUYT_SAO:
                int tileHP = SkillUtil.getPercentHPHuytSao(player.playerSkill.skillSelect);
                int rangeHuytSao = Math.max(0, player.playerSkill.skillSelect.dx);
                if (player.zone != null) {
                    if (!MapService.gI().isMapOffline(player.zone.map.mapId)) {
                        if (!player.isBoss) {
                            List<Player> playersMap = player.zone.getHumanoids();
                            for (Player pl : playersMap) {
                                if (!pl.isBoss && !pl.isDie() && pl.gender != ConstPlayer.NAMEC
                                        && player.cFlag == pl.cFlag
                                        && (player.equals(pl) || Util.getDistance(player, pl) <= rangeHuytSao)) {
                                    applyHuytSao(player, pl, tileHP);
                                } else if (!pl.isBoss && !pl.isDie() && pl.gender == ConstPlayer.NAMEC && player.cFlag == pl.cFlag
                                        && (player.equals(pl) || Util.getDistance(player, pl) <= rangeHuytSao)) {
                                    pl.nPoint.setHP((int) pl.nPoint.hp - (((int) pl.nPoint.hpMax * 10 / 100) < pl.nPoint.hp ? ((int) pl.nPoint.hpMax * 10 / 100) : 0));
                                    Service.gI().point(pl);
                                    Service.gI().Send_Info_NV(pl);
                                }
                            }
                        } else {
                            List<Player> playersMap = player.zone.getBosses();
                            for (Player pl : playersMap) {
                                if (!pl.isDie() && (player.equals(pl) || Util.getDistance(player, pl) <= rangeHuytSao)) {
                                    applyHuytSao(player, pl, tileHP);
                                }
                            }
                        }
                    } else {
                        applyHuytSao(player, player, tileHP);
                    }
                }
                affterUseSkill(player, player.playerSkill.skillSelect.template.id);
                break;
            case Skill.TAI_TAO_NANG_LUONG:
                EffectSkillService.gI().startCharge(player);
                affterUseSkill(player, player.playerSkill.skillSelect.template.id);
                Service.gI().sendEffAllPlayer(player, 284, 1, -1, -1);
                break;
            case Skill.TU_SAT:
                if (!player.playerSkill.prepareTuSat) {
                    //gồng tự sát
                    player.playerSkill.prepareTuSat = true;
                    player.playerSkill.lastTimePrepareTuSat = System.currentTimeMillis();
                    sendPlayerPrepareBom(player, 2000);
                } else {
                    if (!player.isBoss && !player.isPet && !Util.canDoWithTime(player.playerSkill.lastTimePrepareTuSat, 1500)) {
                        player.playerSkill.skillSelect.lastTimeUseThisSkill = System.currentTimeMillis();
                        player.playerSkill.prepareTuSat = false;
                        return false;
                    }
                    if (player.isBoss || player.isPet) {
                        try {
                            Thread.sleep(1500);
                        } catch (InterruptedException e) {
                        }
                    }
                    //nổ
                    player.playerSkill.prepareTuSat = !player.playerSkill.prepareTuSat;
                    int rangeBom = SkillUtil.getRangeBom(player.playerSkill.skillSelect);
                    if (player.setClothes.cadicM == 2) {
                        rangeBom = SkillUtil.getRangeBom(player.playerSkill.skillSelect) + 200;
                    }
                    long dame = player.nPoint.hp;
                    if (player.setClothes.cadicM == 4) {
                        dame += player.nPoint.hpMax * 20 / 100;
                    } else if (player.setClothes.cadicM == 5) {
                        dame += player.nPoint.hpMax * 50 / 100;
                    }
                    dame = SkillMasteryService.gI().applyDamageStat(player.playerSkill.skillSelect,
                            (int) Math.min(Integer.MAX_VALUE, dame));
                    if (!player.isBoss) {
                        for (Mob mob : player.zone.mobs) {
                            if (Util.getDistance(player, mob) <= rangeBom) { //khoảng cách có tác dụng bom
                                mob.injured(player, dame, true);
                            }
                        }
                    }
                    List<Player> playersMap;
                    if (player.isBoss) {
                        playersMap = player.zone.getNotBosses();
                    } else {
                        playersMap = player.zone.getHumanoids();
                    }
                    if (!MapService.gI().isMapOffline(player.zone.map.mapId)) {
                        for (Player pl : playersMap) {
                            if (!player.equals(pl) && canAttackPlayer(player, pl) && Util.getDistance(player, pl) <= rangeBom) {
                                dame = pl.isBoss ? player.effectSkill.isMonkey ? dame / 3 : dame / 2 : dame;
                                pl.injured(player, dame, MapService.gI().isMapYardart(player.zone.map.mapId), false);
                                PlayerService.gI().sendInfoHpMpMoney(pl);
                                Service.gI().Send_Info_NV(pl);
                            }
                        }
                    }
                    affterUseSkill(player, player.playerSkill.skillSelect.template.id);
                    if (!player.isBoss && !player.isPet) {
                        player.setDie();
                    }
                    if (player.effectSkill.tiLeHPHuytSao != 0) {
                        player.effectSkill.tiLeHPHuytSao = 0;
                        EffectSkillService.gI().removeHuytSao(player);
                    }
                }
                break;
        }
        return true;
    }

    private void useSkillBuffToPlayer(Player player, Player plTarget) {
        Message msg = null;
        if (player.playerSkill.skillSelect.template.id == Skill.TRI_THUONG) {
            List<Player> players = new ArrayList<>();
            int congDucNhanDuoc = 0;
            int percentTriThuong = SkillUtil.getPercentTriThuong(player.playerSkill.skillSelect);
            int point = player.playerSkill.skillSelect.point;
            if (canHsPlayer(player, plTarget)) {
                players.add(plTarget);
                List<Player> playersMap = player.zone.getNotBosses();
                for (Player pl : playersMap) {
                    if (!pl.equals(plTarget) && point > 1) {
                        if (canHsPlayer(player, plTarget) && Util.getDistance(player, pl) <= 300) {
                            players.add(pl);
                        }
                    }
                }
                for (Player pl : players) {
                    try {
                        msg = new Message(-60);
                        msg.writer().writeInt((int) player.id); //id pem
                        msg.writer().writeByte(player.playerSkill.skillSelect.skillId); //skill pem
                        msg.writer().writeByte(1); //số người pem
                        msg.writer().writeInt((int) pl.id); //id ăn pem
                        msg.writer().writeByte(0); //read continue
                        Service.gI().sendMessAllPlayerInMap(pl, msg);
                        boolean isDie = pl.isDie();
                        player.nPoint.setHP(player.nPoint.getHP() + ((int) player.nPoint.hpMax * percentTriThuong / 100));
                        pl.nPoint.setHP(pl.nPoint.getHP() + ((int) pl.nPoint.hpMax * percentTriThuong / 100));
                        pl.nPoint.setMP(pl.nPoint.getMP() + ((int) pl.nPoint.mpMax * percentTriThuong / 100));
                        if (!pl.equals(player) && player.gender == ConstPlayer.NAMEC && player.nPoint.congDucTriThuong > 0
                                && player.congDuc < 9999) {
                            int amount = Math.min(player.nPoint.congDucTriThuong, 9999 - player.congDuc);
                            player.congDuc += amount;
                            congDucNhanDuoc += amount;
                        }
                        if (isDie) {
                            AchievementService.gI().checkDoneTask(pl, ConstAchievement.CHAM_SOC_DAC_BIET);
                            Service.gI().chat(pl, "Cảm ơn " + player.name + " đã hồi sinh mình");
                            Service.gI().Send_Info_NV(player);
                            Service.gI().hsChar(pl, pl.nPoint.getHP(), pl.nPoint.getMP());
                            PlayerService.gI().sendInfoHpMpMoney(pl);
                            PlayerService.gI().sendInfoHpMp(player);
                        } else {
                            Service.gI().chat(pl, "Cảm ơn " + player.name + " đã cứu mình");
                            Service.gI().Send_Info_NV(player);
                            PlayerService.gI().sendInfoHpMp(pl);
                            PlayerService.gI().sendInfoHpMp(player);
                        }
                        Service.gI().Send_Info_NV(pl);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (msg != null) {
                            msg.cleanup();
                        }
                    }
                }
                if (congDucNhanDuoc > 0) {
                    Service.gI().sendThongBao(player, "Công đức +" + congDucNhanDuoc + " (" + player.congDuc + "/9999)");
                }
            }
            affterUseSkill(player, player.playerSkill.skillSelect.template.id,
                    canHsPlayer(player, plTarget));
        }
    }

    private void phanSatThuong(Player plAtt, Player plTarget, long dame) {
        if (plAtt != null && plTarget != null && dame > 0) {
            int percentPST = plTarget.nPoint.tlPST;
            long reflectDamage = dame * percentPST / 100L;
            if (isMeleeSkill(plAtt)) {
                reflectDamage += plTarget.nPoint.phanDonCanChien;
            }
            if (reflectDamage > 0) {
                int damePST = (int) Math.min(Integer.MAX_VALUE, reflectDamage);
                Message msg = null;
                try {
                    msg = new Message(56);
                    msg.writer().writeInt((int) plAtt.id);
                    if (damePST >= plAtt.nPoint.hp) {
                        damePST = plAtt.nPoint.hp - 1;
                    }
                    if (plAtt.isBoss && !(plAtt instanceof Broly || plAtt instanceof SuperBroly)) {
                        if (damePST > plAtt.nPoint.hpMax / 100) {
                            int giamdame = 0;
                            if (plAtt.nPoint.hpMax / 200 > 1) {
                                giamdame = Util.nextInt(plAtt.nPoint.hpMax / 200);
                            }
                            damePST = plAtt.nPoint.hpMax / 100 - giamdame;
                        }
                    }
                    damePST = plAtt.injured(plAtt, damePST, true, false);
                    msg.writer().writeInt(plAtt.nPoint.hp);
                    msg.writer().writeInt(damePST);
                    msg.writer().writeBoolean(false);
                    msg.writer().writeByte(36);
                    Service.gI().sendMessAllPlayerInMap(plAtt, msg);
                } catch (Exception e) {
                    Logger.logException(SkillService.class, e);
                } finally {
                    if (msg != null) {
                        msg.cleanup();
                    }
                }
            }
        }
    }

    private void hutHPMP(Player player, long dame, Player pl, Mob mob) {
        int tiLeHutHp = player.nPoint.getTileHutHp(mob != null);
        int tiLeHutMp = player.nPoint.getTiLeHutMp();
        int hpHoi = (int) ((long) dame * tiLeHutHp / 100);
        int mpHoi = (int) ((long) dame * tiLeHutMp / 100);
        if (hpHoi > 0 || mpHoi > 0) {
            int x = -1;
            int y = -1;
            if (pl != null) {
                x = pl.location.x;
                y = pl.location.y;
            } else if (mob != null) {
                x = mob.location.x;
                y = mob.location.y;
            }
            EffectMapService.gI().sendEffectMapToAllInMap(player, 37, 3, 1, x, y, -1);
            PlayerService.gI().hoiPhuc(player, hpHoi, mpHoi);
        }
    }

    void playerAttackPlayer(Player plAtt, Player plInjure, boolean miss) {
        playerAttackPlayer(plAtt, plInjure, miss, 100);
    }

    void playerAttackPlayer(Player plAtt, Player plInjure, boolean miss, int damagePercent) {
        if (plInjure.effectSkill.anTroi) {
            plAtt.nPoint.isCrit100 = true;
        }
        if (plAtt.effectSkin != null && plAtt.playerSkill != null && plAtt.playerSkill.skillSelect != null) {
            plAtt.effectSkin.recordAttackForPunchCombo(plAtt.playerSkill.skillSelect.template.id);
        }
        long dameAttack = plAtt.nPoint.getDameAttack(false);
        if (plAtt.isPl() && plAtt.effectSkin != null && plAtt.effectSkin.isXDame) {
            plAtt.effectSkin.isXDame = false;
            if (plInjure.isBoss) {
                dameAttack /= 3;
            }
        }
        dameAttack = dameAttack * Math.max(0, damagePercent) / 100L;
        int dameHit = plInjure.injured(plAtt, miss ? 0 : dameAttack, false, false);
        if (!miss && dameHit > 0) {
            EquipmentOptionService.gI().onSuccessfulAttack(plAtt, plInjure, null, isMeleeSkill(plAtt));
        }
        if (plAtt.playerSkill == null) {
            return;
        }
        Skill skillSelect = plAtt.playerSkill.skillSelect;
        int damePST = plInjure.effectSkill != null && plInjure.effectSkill.isShielding && plInjure.idMark != null ? plInjure.idMark.getDamePST() : dameHit;
        phanSatThuong(plAtt, plInjure, miss ? 0 : damePST);
        hutHPMP(plAtt, dameHit, plInjure, null);
        if (plInjure instanceof Yardart) {
            if (plInjure.nPoint.hp < dameHit) {
                dameHit = plInjure.nPoint.hp - 1;
                if (dameHit == 0) {
                    return;
                }
            } else if (plInjure.nPoint.hp <= plInjure.nPoint.hpMax / 10) {
                return;
            }
        }
        Message msg = null;
        try {
            msg = new Message(-60);
            msg.writer().writeInt((int) plAtt.id); //id pem
            msg.writer().writeByte(plAtt.playerSkill.skillSelect.skillId); //skill pem
            msg.writer().writeByte(1); //số người pem
            msg.writer().writeInt((int) plInjure.id); //id ăn pem
            msg.writer().writeByte(1); //read continue
            msg.writer().writeByte(0); //type skill
            msg.writer().writeInt(dameHit); //dame ăn
            msg.writer().writeBoolean(plInjure.isDie()); //is die
            msg.writer().writeBoolean(plAtt.nPoint.isCrit); //crit
            Service.gI().sendMessAllPlayerInMap(plAtt, msg);
            Service.gI().reload_HP_NV(plInjure);
            if (plAtt.isPl() && plInjure.isPl() && plAtt.typePk == ConstPlayer.PK_PVP_2 && plInjure.typePk == ConstPlayer.PK_PVP_2) {
                long tnsm = plAtt.nPoint.calSucManhTiemNangPvp(dameHit / 10) / (Math.abs(Service.gI().getCurrLevel(plAtt) - Service.gI().getCurrLevel(plInjure)) + 1);
                Service.gI().addSMTN(plInjure, (byte) 2, tnsm, false);
            }
            if (plInjure.isDie() && !plAtt.isBoss && !plInjure.isBoss && MapService.gI().isMapMaBu(plInjure.zone.map.mapId)) {
                plAtt.fightMabu.changePoint((byte) 5);
            }
        } catch (Exception e) {
            Logger.logException(SkillService.class, e);
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    void playerAttackMob(Player plAtt, Mob mob, boolean miss, boolean dieWhenHpFull) {
        playerAttackMob(plAtt, mob, miss, dieWhenHpFull, 100, true);
    }

    void playerAttackMob(Player plAtt, Mob mob, boolean miss, boolean dieWhenHpFull,
            int damagePercent, boolean sendNormalAnimation) {
        if (mob == null || mob.isDie() || plAtt == null || plAtt.nPoint == null || plAtt.playerSkill == null) {
            return;
        }

        if (plAtt.effectSkin != null) {
            plAtt.effectSkin.lastTimeAttackMob = System.currentTimeMillis();
        }

        if (plAtt.effectSkin != null && plAtt.playerSkill.skillSelect != null) {
            plAtt.effectSkin.recordAttackForPunchCombo(plAtt.playerSkill.skillSelect.template.id);
        }
        long dameHit = plAtt.nPoint.getDameAttack(true);

        if (plAtt.effectSkin != null && plAtt.effectSkin.isXDame) {
            plAtt.effectSkin.isXDame = false;
        }

        if (plAtt.charms != null && plAtt.charms.tdBatTu > System.currentTimeMillis() && plAtt.nPoint.hp <= 1) {
            if (plAtt.nPoint.hp < 1) {
                plAtt.nPoint.hp = 1;
            }
            if (!plAtt.isPet) {
                dameHit = 0;
                Service.gI().sendThongBao(plAtt, "Bạn đang được bùa bất tử bảo vệ không thể tấn công!");
            }
        }

        if (plAtt.charms != null && plAtt.charms.tdManhMe > System.currentTimeMillis()) {
            dameHit += (dameHit * 150 / 100);
        }

        if (plAtt.isPet) {
            Pet pet = (Pet) plAtt;
            if (pet.master != null && pet.master.charms != null && pet.master.charms.tdDeTu > System.currentTimeMillis()) {
                dameHit *= 2;
            }
        }

        if (miss) {
            dameHit = 0;
        }

        dameHit = dameHit * Math.max(0, damagePercent) / 100L;
        dameHit = Math.min(dameHit, 2_147_483_647);

        hutHPMP(plAtt, dameHit, null, mob);
        if (sendNormalAnimation) {
            sendPlayerAttackMob(plAtt, mob);
        }
        int hpBefore = mob.point.gethp();
        mob.injured(plAtt, dameHit, dieWhenHpFull);
        if (!miss && hpBefore > mob.point.gethp()) {
            EquipmentOptionService.gI().onSuccessfulAttack(plAtt, null, mob, isMeleeSkill(plAtt));
        }
    }

    public void sendPlayerPrepareSkill(Player player, int affterMiliseconds) {
        Message msg = null;
        try {
            msg = new Message(-45);
            msg.writer().writeByte(4);
            msg.writer().writeInt((int) player.id);
            msg.writer().writeShort(player.playerSkill.skillSelect.skillId);
            msg.writer().writeShort(affterMiliseconds);
            Service.gI().sendMessAllPlayerInMap(player, msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void sendPlayerPrepareBom(Player player, int affterMiliseconds) {
        Message msg = null;
        try {
            msg = new Message(-45);
            msg.writer().writeByte(7);
            msg.writer().writeInt((int) player.id);
            msg.writer().writeShort(player.playerSkill.skillSelect.skillId);
            msg.writer().writeShort(affterMiliseconds);
            Service.gI().sendMessAllPlayerInMap(player, msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public boolean canUseSkillWithMana(Player player) {
        if (player.playerSkill.skillSelect != null) {
            if (!ItemService.gI().canUseEquippedSkillBook(player)) {
                return false;
            }
            if (player.playerSkill.skillSelect.template.id == Skill.KAIOKEN) {
                long hpUse = player.nPoint.hpMax / 100 * 10;
                if (isKaioSetManaReductionActive(player)) {
                    hpUse /= 2;
                }
                if (player.isBoss && player instanceof Rival) {
                    hpUse = 0;
                }
                if (player.nPoint.hp <= hpUse) {
                    return false;
                }
            }
            switch (player.playerSkill.skillSelect.template.manaUseType) {
                case 0 -> {
                    return player.nPoint.mp >= getManaUse(player);
                }
                case 1 -> {
                    int mpUse = getManaUse(player);
                    return player.nPoint.mp >= mpUse;
                }
                case 2 -> {
                    return player.nPoint.mp > 0;
                }
                default -> {
                    return false;
                }
            }
        } else {
            return false;
        }
    }

    public boolean canUseSkillWithCooldown(Player player) {
        int cooldown = player.effectSkin != null && player.effectSkin.isOption224Active()
                ? 100 : player.playerSkill.skillSelect.coolDown;
        return Util.canDoWithTime(player.playerSkill.skillSelect.lastTimeUseThisSkill,
                Math.max(0, cooldown - 50));
    }

    public void affterUseSkill(Player player, int skillId) {
        affterUseSkill(player, skillId, true);
    }

    public void affterUseSkill(Player player, int skillId, boolean validMasteryUse) {
        Intrinsic intrinsic = player.playerIntrinsic.intrinsic;
        switch (skillId) {
            case Skill.DICH_CHUYEN_TUC_THOI -> {
                if (intrinsic.id == 6) {
                    player.nPoint.dameAfter = intrinsic.param1;
                }
            }
            case Skill.THOI_MIEN -> {
                if (intrinsic.id == 7) {
                    player.nPoint.dameAfter = intrinsic.param1;
                }
            }
            case Skill.SOCOLA -> {
                if (intrinsic.id == 14) {
                    player.nPoint.dameAfter = intrinsic.param1;
                }
            }
            case Skill.TROI -> {
                if (intrinsic.id == 22) {
                    player.nPoint.dameAfter = intrinsic.param1;
                }
            }
        }
        setMpAffterUseSkill(player);
        setLastTimeUseSkill(player, skillId);
        ItemService.gI().consumeEquippedSkillBookDurability(player);
        if (validMasteryUse) {
            SkillMasteryService.gI().recordValidUse(player, player.playerSkill.skillSelect);
        }
    }

    private boolean isMeleeSkill(Player attacker) {
        if (attacker == null || attacker.playerSkill == null || attacker.playerSkill.skillSelect == null) {
            return false;
        }
        return switch (attacker.playerSkill.skillSelect.template.id) {
            case Skill.DRAGON, Skill.DEMON, Skill.GALICK, Skill.LIEN_HOAN, Skill.KAIOKEN -> true;
            default -> false;
        };
    }

    /** Applies the 30-second Huýt Sáo buff while preserving the target's HP percentage. */
    private void applyHuytSao(Player caster, Player target, int percentHp) {
        if (target == null || target.nPoint == null || target.effectSkill == null) {
            return;
        }
        if (target.effectSkill.useTroi) {
            EffectSkillService.gI().removeUseTroi(target);
        }
        long hpBefore = target.nPoint.hp;
        long hpMaxBefore = Math.max(1L, target.nPoint.hpMax);
        EffectSkillService.gI().setStartHuytSao(target, percentHp);
        EffectSkillService.gI().sendEffectPlayer(caster, target, EffectSkillService.TURN_ON_EFFECT,
                EffectSkillService.HUYT_SAO_EFFECT);
        target.nPoint.calPoint();
        target.nPoint.setHP(hpBefore * target.nPoint.hpMax / hpMaxBefore);
        target.nPoint.isCrit100 = true;
        Service.gI().point(target);
        Service.gI().Send_Info_NV(target);
        ItemTimeService.gI().sendItemTime(target, 3781, 30);
        PlayerService.gI().sendInfoHpMp(target);
    }

    private void setMpAffterUseSkill(Player player) {
        if (player.playerSkill.skillSelect != null) {
            switch (player.playerSkill.skillSelect.template.manaUseType) {
                case 0 -> {
                    int mpUse = getManaUse(player);
                    if (player.nPoint.mp >= mpUse) {
                        player.nPoint.setMp(player.nPoint.mp - mpUse);
                    }
                }
                case 1 -> {
                    int mpUse = getManaUse(player);
                    if (player.nPoint.mp >= mpUse) {
                        player.nPoint.setMp(player.nPoint.mp - mpUse);
                    }
                }
                case 2 -> {
                    if (isKaioSetManaReductionActive(player)) {
                        player.nPoint.setMp(player.nPoint.mp / 2L);
                    } else {
                        player.nPoint.setMp(0);
                    }
                }
            }
            PlayerService.gI().sendInfoHpMpMoney(player);
        }
    }

    private void setLastTimeUseSkill(Player player, int skillId) {
        Intrinsic intrinsic = player.playerIntrinsic.intrinsic;
        int subTimeParam = 0;
        switch (skillId) {
            case Skill.TRI_THUONG -> {
                if (intrinsic.id == 10) {
                    subTimeParam = intrinsic.param1;
                }
            }
            case Skill.THAI_DUONG_HA_SAN -> {
                if (intrinsic.id == 3) {
                    subTimeParam = intrinsic.param1;
                }
            }
            case Skill.QUA_CAU_KENH_KHI -> {
                if (intrinsic.id == 4) {
                    subTimeParam = intrinsic.param1;
                }
            }
            case Skill.KHIEN_NANG_LUONG -> {
                if (intrinsic.id == 5 || intrinsic.id == 15 || intrinsic.id == 20) {
                    subTimeParam = intrinsic.param1;
                }
                if (player.nPoint != null) {
                    subTimeParam += Math.max(0, player.nPoint.shieldCooldownReductionPercent);
                }
            }
            case Skill.MAKANKOSAPPO -> {
                if (intrinsic.id == 11) {
                    subTimeParam = intrinsic.param1;
                }
            }
            case Skill.DE_TRUNG -> {
                if (intrinsic.id == 12) {
                    subTimeParam = intrinsic.param1;
                }
            }
            case Skill.TU_SAT -> {
                if (intrinsic.id == 19) {
                    subTimeParam = intrinsic.param1;
                }
            }
            case Skill.HUYT_SAO -> {
                if (intrinsic.id == 21) {
                    subTimeParam = intrinsic.param1;
                }
            }
            case Skill.MASENKO -> {
                if (intrinsic.id == 9) {
                    subTimeParam = intrinsic.param1;
                }
                if (player.setClothes.nail == 4) {
                    subTimeParam = subTimeParam + 20; // Nếu nail = 4, cộng thêm 20
                }

                if (player.setClothes.nail == 5) {
                    subTimeParam = subTimeParam + 50; // Nếu nail = 5, cộng thêm 50
                }
            }
        }
        player.playerSkill.skillSelect.lastTimeUseThisSkill = System.currentTimeMillis() - 1;
        subTimeParam = Math.min(100, Math.max(0, subTimeParam));
        int coolDown = player.effectSkin != null && player.effectSkin.isOption224Active()
                ? 100 : player.playerSkill.skillSelect.coolDown;
        long lastTimeUseSkill = System.currentTimeMillis() - ((long) coolDown * subTimeParam / 100);
        if (subTimeParam != 0) {
            EffectSkillService.gI().setIntrinsic(player, skillId, coolDown, lastTimeUseSkill);
        }
    }

    /** The four- and five-piece Kaio sets cut Kaioken's HP and KI consumption in half. */
    private boolean isKaioSetManaReductionActive(Player player) {
        return player != null && player.playerSkill != null && player.playerSkill.skillSelect != null
                && player.playerSkill.skillSelect.template.id == Skill.KAIOKEN
                && player.setClothes != null && player.setClothes.thanVuTruKaio >= 4;
    }

    private int getManaUse(Player player) {
        Skill skill = player.playerSkill.skillSelect;
        int manaUse = switch (skill.template.manaUseType) {
            case 0 -> skill.manaUse;
            case 1 -> player.nPoint.mpMax * skill.manaUse / 100;
            default -> 0;
        };
        if (isKaioSetManaReductionActive(player)) {
            manaUse = Math.max(1, manaUse / 2);
        }
        return manaUse;
    }

    private boolean canHsPlayer(Player player, Player plTarget) {
        if (plTarget == null) {
            return false;
        }
        if (plTarget.isBoss) {
            return false;
        }
        if (plTarget.typePk == ConstPlayer.PK_ALL) {
            return false;
        }
        if (plTarget.typePk == ConstPlayer.PK_PVP) {
            return false;
        }
        if (player.cFlag != 0) {
            if (plTarget.cFlag != 0 && plTarget.cFlag != player.cFlag) {
                return false;
            }
        } else {
            return plTarget.cFlag == 0;
        }
        return true;
    }

    public boolean canAttackPlayer(Player p1, Player p2) {
        if (p1.isDie() || p2.isDie()) {
            return false;
        }

        return canAttackPlayer2(p1, p2);
    }

    public boolean canAttackPlayer2(Player p1, Player p2) {

        if (p1.isNewPet || p2.isNewPet || (p1 instanceof NonInteractiveNPC) || (p2 instanceof NonInteractiveNPC)) {
            return false;
        }

        if (p1.typePk == ConstPlayer.PK_ALL || p2.typePk == ConstPlayer.PK_ALL) {
            return true;
        }
        if (p1.isPl() && p2.isPl() && (p1.idMark != null && p1.idMark.getKillCharId() == p2.id
                || p2.idMark != null && p2.idMark.getKillCharId() == p1.id)) {
            return true;
        }
        if ((p1.cFlag != 0 && p2.cFlag != 0)
                && (p1.cFlag == 8 || p2.cFlag == 8 || p1.cFlag != p2.cFlag)) {
            return true;
        }
        if (p1.pvp == null || p2.pvp == null) {
            return false;
        }
        return p1.pvp.isInPVP(p2) || p2.pvp.isInPVP(p1);
    }

    private void sendPlayerAttackMob(Player plAtt, Mob mob) {
        Message msg = null;
        try {
            msg = new Message(54);
            msg.writer().writeInt((int) plAtt.id);
            msg.writer().writeByte(plAtt.playerSkill.skillSelect.skillId);
            msg.writer().writeByte(mob.id);
            Service.gI().sendMessAllPlayerInMap(plAtt, msg);
        } catch (IOException e) {
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void selectSkill(Player player, int skillId) {
        for (Skill skill : player.playerSkill.skills) {
            if (skill.skillId != -1 && skill.template.id == skillId) {
                player.playerSkill.skillSelect = skill;
                break;
            }
        }
    }
}
