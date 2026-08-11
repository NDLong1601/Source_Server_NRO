package nro.models.player;

import nro.models.consts.ConstPlayer;
import nro.models.item.Item;
import lombok.Getter;
import nro.models.map.service.MapService;
import nro.models.mob.Mob;
import nro.models.skill.Skill;
import nro.models.utils.SkillUtil;
import nro.models.services.Service;
import nro.models.utils.Util;
import nro.models.network.Message;
import nro.models.services.ItemTimeService;
import nro.models.services.PlayerService;
import nro.models.services.SkillService;
import nro.models.map.service.ChangeMapService;
import nro.models.utils.TimeUtil;
import java.util.List;
import nro.models.consts.ConstAchievement;
import nro.models.services.AchievementService;
import nro.models.services_func.UseItem;

/**
 *
 * @author By Mr Blue
 *
 */
public class Pet extends Player {

    private static final short[][] PET_ID = {{285, 286, 287}, {288, 289, 290}, {282, 283, 284}, {304, 305, 303}, {946, 947, 948}, {1743, 1744, 1745}, {876, 877, 878}};

    public static final byte FOLLOW = 0;
    public static final byte PROTECT = 1;
    public static final byte ATTACK = 2;
    public static final byte GOHOME = 3;
    public static final byte FUSION = 4;
    public static final byte HTVV = 5;

    public Player master;
    @Getter
    public byte status = 0;

    public byte typePet;
    public boolean isTransform;

    public long lastTimeDie;

    private boolean goingHome;

    private Mob mobAttack;
    private Player playerAttack;

    private long lastTimeUnfusion;

    private int indexChat = 0;
    private long lastTimeChat;

    public byte typePaint;
    public byte typeItem;

    public Pet(Player master) {
        this.master = master;
        this.isPet = true;
    }

    public void changeStatus(byte status) {
        if (goingHome || master.fusion.typeFusion != 0 || (this.isDie() && status == FUSION)) {
            Service.gI().sendThongBao(master, "Không thể thực hiện");
            return;
        }
        Service.gI().chatJustForMe(master, this, getTextStatus(status));
        if (status == GOHOME) {
            goHome();
        } else if (status == FUSION) {
            fusion(false);
        }
        this.status = status;
    }

    public void joinMapMaster() {
        if (status != GOHOME && status != FUSION && !isDie()) {
            this.location.x = master.location.x + Util.nextInt(-10, 10);
            this.location.y = master.location.y;
            if (MapService.gI().isMapOffline(this.master.zone.map.mapId) || this.master.zone.map.mapId == 113) {
                ChangeMapService.gI().goToMap(this, MapService.gI().getMapCanJoin(this, master.gender + 21, -1));
                return;
            }
            ChangeMapService.gI().goToMap(this, master.zone);
            this.zone.load_Me_To_Another(this);
        }
    }

    public void goHome() {
        if (this.status == GOHOME) {
            return;
        }
        goingHome = true;
        new Thread(() -> {
            try {
                Pet.this.status = Pet.ATTACK;
                Thread.sleep(2000);
            } catch (Exception e) {
            }
            if (master != null) {
                try {
                    ChangeMapService.gI().goToMap(this, MapService.gI().getMapCanJoin(this, master.gender + 21, -1));
                } catch (Exception e) {
                }
                this.zone.load_Me_To_Another(this);
                Pet.this.status = Pet.GOHOME;
                goingHome = false;
            }
        }).start();
    }

    private String getTextStatus(byte status) {
        if (this.typePet == 4) {
            switch (status) {
                case FOLLOW:
                    return "Lũ con người không đủ tư cách để nói chuyện với ta";
                case PROTECT:
                    return "Ta sẽ cho người biết sức mạnh của một vị thần là như thế nào !";
                case ATTACK:
                    return "Ta sẽ thống trị vũ trụ";
                case GOHOME:
                    return "Không lí nào ta lại run sợ bọn con người sao";
                case HTVV:
                    return "Lũ các ngươi làm ta thấy đau rồi ấy haha";
                default:
                    return "Sức mạnh của ta là không có giới hạn";
            }
        }
        switch (status) {
            case FOLLOW:
                return "Ok con theo sư phụ";
            case PROTECT:
                return "Ok con sẽ bảo vệ sư phụ";
            case ATTACK:
                return "Ok sư phụ để con lo cho";
            case GOHOME:
                return "OK con về, bibi sư phụ";
            case HTVV:
                return "Dm sư phụ";
            default:
                return "Sư phụ ơi con lên cấp rồi";
        }
    }

    public void fusionGogeta(boolean porata) {
        if (this.isDie()) {
            Service.gI().sendThongBao(master, "Đệ cu chết rồi hợp thể chóa giề");
            return;
        }
        if (Util.canDoWithTime(lastTimeUnfusion, PetConfig.getUnfusionCooldownMs())) {
            if (porata) {
                master.fusion.typeFusion = ConstPlayer.HOP_THE_GOGETA;
            } else {
                master.fusion.lastTimeFusion = System.currentTimeMillis();
                master.fusion.typeFusion = ConstPlayer.LUONG_LONG_NHAT_THE;
                ItemTimeService.gI().sendItemTime(master, master.gender == ConstPlayer.NAMEC ? 3901 : 3790,
                        PetConfig.getFusionDurationMs() / 1000);
            }
            this.status = FUSION;
            ChangeMapService.gI().exitMap(this);
            fusionEffect(master.fusion.typeFusion);
            Service.gI().Send_Caitrang(master);
            master.nPoint.calPoint();
            master.nPoint.setFullHpMp();
            Service.gI().point(master);
            if (master.fusion.typeFusion != ConstPlayer.NON_FUSION) {
                Item item = master.inventory.itemsBody.get(5);
                Item petItem = this.inventory.itemsBody.get(5);
                boolean hasItem = item.isNotNullItem() && (item.template.id == 1693 || item.template.id == 1553);
                boolean sameItem = item.isNotNullItem() && petItem.isNotNullItem() && item.template.id == petItem.template.id;
                if (hasItem && !sameItem) {
                    SkillService.gI().sendPlayerPrepareBom(master, 2000);
                }
            }
        } else {
            Service.gI().sendThongBao(this.master, "Vui lòng đợi "
                    + TimeUtil.getTimeLeft(lastTimeUnfusion, PetConfig.getUnfusionCooldownMs() / 1000) + " nữa");
        }
    }

    public void fusion2(boolean porata) {
        if (this.isDie()) {
            Service.gI().sendThongBao(master, "Yêu cầu phải có đệ tử và đệ tử còn sống");
            return;
        }
        if (Util.canDoWithTime(lastTimeUnfusion, PetConfig.getUnfusionCooldownMs())) {
            if (porata) {
                master.fusion.typeFusion = ConstPlayer.HOP_THE_PORATA2;
            }
            this.status = FUSION;
            ChangeMapService.gI().exitMap(this);
            fusionEffect(master.fusion.typeFusion);
            master.nPoint.calPoint();
            master.nPoint.setFullHpMp();
            Service.gI().point(master);
            Service.gI().Send_Caitrang(master);
        } else {
            Service.gI().sendThongBao(this.master, "Vui lòng đợi "
                    + TimeUtil.getTimeLeft(lastTimeUnfusion, PetConfig.getUnfusionCooldownMs() / 1000) + " nữa");
        }
    }
    public void fusion3(boolean porata) {
        if (this.isDie()) {
            Service.gI().sendThongBao(master, "Yêu cầu phải có đệ tử và đệ tử còn sống");
            return;
        }
        if (Util.canDoWithTime(lastTimeUnfusion, PetConfig.getUnfusionCooldownMs())) {
            if (porata) {
                master.fusion.typeFusion = ConstPlayer.HOP_THE_PORATA3;
            }
            this.status = FUSION;
            ChangeMapService.gI().exitMap(this);
            fusionEffect(master.fusion.typeFusion);
            master.nPoint.calPoint();
            master.nPoint.setFullHpMp();
            Service.gI().point(master);
            Service.gI().Send_Caitrang(master);
        } else {
            Service.gI().sendThongBao(this.master, "Vui lòng đợi "
                    + TimeUtil.getTimeLeft(lastTimeUnfusion, PetConfig.getUnfusionCooldownMs() / 1000) + " nữa");
        }
    }

    public void fusion(boolean porata) {
        if (this.isDie()) {
            Service.gI().sendThongBao(master, "Yêu cầu phải có đệ tử và đệ tử còn sống");
            return;
        }
        if (Util.canDoWithTime(lastTimeUnfusion, PetConfig.getUnfusionCooldownMs())) {
            if (porata) {
                master.fusion.typeFusion = ConstPlayer.HOP_THE_PORATA;
            } else {
                master.fusion.lastTimeFusion = System.currentTimeMillis();
                master.fusion.typeFusion = ConstPlayer.LUONG_LONG_NHAT_THE;
                ItemTimeService.gI().sendItemTime(master, master.gender == ConstPlayer.NAMEC ? 3901 : 3790, PetConfig.getFusionDurationMs() / 1000);
            }
            this.status = FUSION;
            ChangeMapService.gI().exitMap(this);
            fusionEffect(master.fusion.typeFusion);
            Service.gI().Send_Caitrang(master);
            master.nPoint.calPoint();
            master.nPoint.setFullHpMp();
            Service.gI().point(master);
        } else {
            Service.gI().sendThongBao(this.master, "Vui lòng đợi "
                    + TimeUtil.getTimeLeft(lastTimeUnfusion, PetConfig.getUnfusionCooldownMs() / 1000) + " nữa");
        }
    }

    public void unFusion() {
        master.fusion.typeFusion = 0;
        this.status = PROTECT;
        Service.gI().point(master);
        joinMapMaster();
        fusionEffect(master.fusion.typeFusion);
        Service.gI().Send_Caitrang(master);
        Service.gI().point(master);
        this.lastTimeUnfusion = System.currentTimeMillis();
    }

    private void fusionEffect(int type) {
        Message msg;
        try {
            msg = new Message(125);
            msg.writer().writeByte(type);
            msg.writer().writeInt((int) master.id);
            Service.gI().sendMessAllPlayerInMap(master, msg);
            msg.cleanup();
        } catch (Exception e) {

        }
    }

    public long lastTimeMoveIdle;
    private int timeMoveIdle;
    public boolean idle;

    private void moveIdle() {
        if (status == GOHOME || status == FUSION || status == HTVV) {
            return;
        }
        if (idle && Util.canDoWithTime(lastTimeMoveIdle, timeMoveIdle)) {
            int dir = this.location.x - master.location.x <= 0 ? -1 : 1;
            PlayerService.gI().playerMove(this, master.location.x
                    + (dir == -1 ? 50 : -50), master.location.y);
            lastTimeMoveIdle = System.currentTimeMillis();
            timeMoveIdle = Util.nextInt(5000, 8000);
            idle = false;
        }
    }

    private void masterDoesNotAttack() {
        if (Util.canDoWithTime(master.lastTimePlayerNotAttack, master.timeNotAttack)) {
            if (!MapService.gI().isMapOffline(master.zone.map.mapId)) {
                master.doesNotAttack = true;
            }
            master.lastTimePlayerNotAttack = System.currentTimeMillis();
            master.timeNotAttack = Util.nextInt(1800000, 3600000);
        }
    }

    private long lastTimeMoveAtHome;
    private byte directAtHome = -1;

    @Override
    public void update() {
        try {
            if (this.master != null && this.master.zone != null) {
                super.update();
                increasePoint();
                updatePower();

                if (isDie()) {
                    if (System.currentTimeMillis() - lastTimeDie > PetConfig.getInt("pet.ai.reviveDelayMs", 120_000, 0, Integer.MAX_VALUE)) {
                        Service.gI().hsChar(this, nPoint.hpMax, nPoint.mpMax);
                    } else {
                        return;
                    }
                }

                if (this.newSkill != null && this.newSkill.isStartSkillSpecial) {
                    return;
                }

                if (justRevived && this.zone == master.zone) {
                    Service.gI().chatJustForMe(master, this, "Sư phụ ơi con đây nè");
                    justRevived = false;
                }

                if (this.zone == null || this.zone != master.zone) {
                    joinMapMaster();
                }

                if (master.isDie() || this.isDie() || effectSkill.isHaveEffectSkill()) {
                    return;
                }

                masterDoesNotAttack();
                moveIdle();

                switch (status) {
                    case FOLLOW:
                        followMaster(PetConfig.getInt("pet.ai.followIdleDistance", 60, 1, 10_000));
                        break;

                    case PROTECT:
                        if (useSkill3() || useSkill4() || useSkill5()) {
                            break;
                        }

                        playerAttack = findPlayerAttack();
                        if (playerAttack != null) {
                            if (PetConfig.getBoolean("pet.ai.hakaiEnabled", true)
                                    && PetConfig.isTypeAllowed("pet.ai.hakaiAllowedTypes", this.typePet, 2, 4)
                                    && Util.isTrue(PetConfig.getInt("pet.ai.hakaiRatePercent", 20, 0, 100), 100)
                                    && playerAttack.nPoint.hp < PetConfig.getInt("pet.ai.hakaiMaxTargetHp", 1_000_000_000, 1, Integer.MAX_VALUE)
                                    && !playerAttack.nPoint.islinhthuydanhbac
                                    && !playerAttack.isBoss) {
                                playerAttack.setDie(this);
                                Service.gI().chat(this, "HAKAI " + playerAttack.name + "!");
                                Service.gI().sendThongBao(playerAttack, "Bạn đã bị Hakai!");
                            } else {
                                petSay(playerAttack);
                            }

                            int disToPlayer = Util.getDistance(this, playerAttack);
                            if (disToPlayer <= PetConfig.getInt("pet.ai.meleeRange", 50, 1, 10_000)) {
                                this.playerSkill.skillSelect = getSkill(1);
                                if (SkillService.gI().canUseSkillWithCooldown(this) && canAttack()) {
                                    if (SkillService.gI().canUseSkillWithMana(this)) {
                                        PlayerService.gI().playerMove(this, playerAttack.location.x + Util.nextInt(-60, 60), playerAttack.location.y);
                                        SkillService.gI().useSkill(this, playerAttack, null, -1, null);
                                    } else {
                                        askPea();
                                    }
                                }
                            } else {
                                this.playerSkill.skillSelect = getSkill(2);
                                if (this.playerSkill.skillSelect.skillId != -1) {
                                    if (SkillService.gI().canUseSkillWithCooldown(this) && canAttack()) {
                                        if (SkillService.gI().canUseSkillWithMana(this)) {
                                            SkillService.gI().useSkill(this, playerAttack, null, -1, null);
                                        } else {
                                            askPea();
                                        }
                                    }
                                } else {
                                    this.playerSkill.skillSelect = getSkill(1);
                                    if (SkillService.gI().canUseSkillWithCooldown(this) && canAttack()) {
                                        if (SkillService.gI().canUseSkillWithMana(this)) {
                                            PlayerService.gI().playerMove(this, playerAttack.location.x + Util.nextInt(-60, 60), playerAttack.location.y);
                                            SkillService.gI().useSkill(this, playerAttack, null, -1, null);
                                        } else {
                                            askPea();
                                        }
                                    }
                                }
                            }
                            return;
                        }

                        mobAttack = findMobAttack();
                        if (mobAttack != null) {
                            int disToMob = Util.getDistance(this, mobAttack);
                            if (disToMob <= PetConfig.getInt("pet.ai.meleeRange", 50, 1, 10_000)) {
                                this.playerSkill.skillSelect = getSkill(1);
                                if (SkillService.gI().canUseSkillWithCooldown(this) && canAttack()) {
                                    if (SkillService.gI().canUseSkillWithMana(this)) {
                                        PlayerService.gI().playerMove(this, mobAttack.location.x + Util.nextInt(-60, 60), mobAttack.location.y);
                                        SkillService.gI().useSkill(this, null, mobAttack, -1, null);
                                    } else {
                                        askPea();
                                    }
                                }
                            } else {
                                this.playerSkill.skillSelect = getSkill(2);
                                if (this.playerSkill.skillSelect.skillId != -1) {
                                    if (SkillService.gI().canUseSkillWithCooldown(this) && canAttack()) {
                                        if (SkillService.gI().canUseSkillWithMana(this)) {
                                            SkillService.gI().useSkill(this, null, mobAttack, -1, null);
                                        } else {
                                            askPea();
                                        }
                                    }
                                } else {
                                    this.playerSkill.skillSelect = getSkill(1);
                                    if (SkillService.gI().canUseSkillWithCooldown(this) && canAttack()) {
                                        if (SkillService.gI().canUseSkillWithMana(this)) {
                                            PlayerService.gI().playerMove(this, mobAttack.location.x + Util.nextInt(-60, 60), mobAttack.location.y);
                                            SkillService.gI().useSkill(this, null, mobAttack, -1, null);
                                        } else {
                                            askPea();
                                        }
                                    }
                                }
                            }
                        } else {
                            idle = true;
                        }

                        break;

                    case ATTACK:
                        if (useSkill3() || useSkill4() || useSkill5()) {
                            break;
                        }

                        playerAttack = findPlayerAttack();
                        if (playerAttack != null) {
                            int disToPlayer = Util.getDistance(this, playerAttack);
                            if (disToPlayer <= PetConfig.getInt("pet.ai.meleeRange", 50, 1, 10_000)) {
                                this.playerSkill.skillSelect = getSkill(1);
                                if (SkillService.gI().canUseSkillWithCooldown(this) && canAttack()) {
                                    if (SkillService.gI().canUseSkillWithMana(this)) {
                                        PlayerService.gI().playerMove(this, playerAttack.location.x + Util.nextInt(-60, 60), playerAttack.location.y);
                                        SkillService.gI().useSkill(this, playerAttack, null, -1, null);
                                        if (getSkill(5) != null) {
                                            useSkill5();
                                        }
                                    } else {
                                        askPea();
                                    }
                                }
                            } else {
                                this.playerSkill.skillSelect = getSkill(2);
                                if (this.playerSkill.skillSelect.skillId != -1) {
                                    if (SkillService.gI().canUseSkillWithCooldown(this) && canAttack()) {
                                        if (SkillService.gI().canUseSkillWithMana(this)) {
                                            SkillService.gI().useSkill(this, playerAttack, null, -1, null);
                                        } else {
                                            askPea();
                                        }
                                    }
                                } else {
                                    this.playerSkill.skillSelect = getSkill(1);
                                    if (SkillService.gI().canUseSkillWithCooldown(this) && canAttack()) {
                                        if (SkillService.gI().canUseSkillWithMana(this)) {
                                            PlayerService.gI().playerMove(this, playerAttack.location.x + Util.nextInt(-60, 60), playerAttack.location.y);
                                            SkillService.gI().useSkill(this, playerAttack, null, -1, null);
                                        } else {
                                            askPea();
                                        }
                                    }
                                }
                            }
                            return;
                        }

                        mobAttack = findMobAttack();
                        if (mobAttack != null) {
                            int disToMob = Util.getDistance(this, mobAttack);
                            if (disToMob <= PetConfig.getInt("pet.ai.meleeRange", 50, 1, 10_000)) {
                                this.playerSkill.skillSelect = getSkill(1);
                                if (SkillService.gI().canUseSkillWithCooldown(this) && canAttack()) {
                                    if (SkillService.gI().canUseSkillWithMana(this)) {
                                        PlayerService.gI().playerMove(this, mobAttack.location.x + Util.nextInt(-20, 20), mobAttack.location.y);
                                        SkillService.gI().useSkill(this, null, mobAttack, -1, null);
                                    } else {
                                        askPea();
                                    }
                                }
                            } else {
                                this.playerSkill.skillSelect = getSkill(2);
                                if (this.playerSkill.skillSelect.skillId != -1) {
                                    if (SkillService.gI().canUseSkillWithMana(this)) {
                                        PlayerService.gI().playerMove(this, mobAttack.location.x + Util.nextInt(-20, 20), mobAttack.location.y);
                                        SkillService.gI().useSkill(this, null, mobAttack, -1, null);
                                    }
                                } else {
                                    this.playerSkill.skillSelect = getSkill(1);
                                    if (SkillService.gI().canUseSkillWithCooldown(this) && canAttack()) {
                                        if (SkillService.gI().canUseSkillWithMana(this)) {
                                            PlayerService.gI().playerMove(this, mobAttack.location.x + Util.nextInt(-20, 20), mobAttack.location.y);
                                            SkillService.gI().useSkill(this, null, mobAttack, -1, null);
                                        } else {
                                            askPea();
                                        }
                                    }
                                }
                            }
                        } else {
                            idle = true;
                        }

                        break;

                    case GOHOME:
                        if (this.zone != null && (this.zone.map.mapId == 21 || this.zone.map.mapId == 22 || this.zone.map.mapId == 23)) {
                            if (System.currentTimeMillis() - lastTimeMoveAtHome > 5000) {
                                int[] coords = switch (this.zone.map.mapId) {
                                    case 21 ->
                                        directAtHome == -1 ? new int[]{250, 336} : new int[]{200, 336};
                                    case 22 ->
                                        directAtHome == -1 ? new int[]{500, 336} : new int[]{452, 336};
                                    case 23 ->
                                        directAtHome == -1 ? new int[]{250, 336} : new int[]{200, 336};
                                    default ->
                                        new int[]{200, 336};
                                };
                                PlayerService.gI().playerMove(this, coords[0], coords[1]);
                                directAtHome *= -1;
                                Service.gI().chatJustForMe(master, this, "Là do bạn không chơi đồ đấy bạn ạ!");
                                lastTimeMoveAtHome = System.currentTimeMillis();
                            }
                        }
                        break;

                    case HTVV:
                        if (master.gender == 1) {
                            fusionEffect(ConstPlayer.LUONG_LONG_NHAT_THE);
                            ChangeMapService.gI().exitMap(this);
                            Service.gI().addSMTN(master, (byte) 1, this.nPoint.power, true);
                            master.pet = null;
                            Service.gI().sendHavePet(master);
                        }
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private long lastTimeAskPea;

    public void askPea() {
        if (Util.canDoWithTime(lastTimeAskPea, PetConfig.getInt("pet.ai.peaCooldownMs", 10_000, 0, Integer.MAX_VALUE))) {
            if (this.master.isPet) {
                if (!this.isDie()) {
                    int statima = 100 * 10;
                    int hpKiHoiPhuc = 100000;
                    this.nPoint.stamina += statima;
                    if (this.nPoint.stamina > this.nPoint.maxStamina) {
                        this.nPoint.stamina = this.nPoint.maxStamina;
                    }
                    this.nPoint.setHp(this.nPoint.hp + hpKiHoiPhuc);
                    this.nPoint.setMp(this.nPoint.mp + hpKiHoiPhuc);
                    Service.gI().sendInfoPlayerEatPea(this);
                }
                lastTimeAskPea = System.currentTimeMillis();
                return;
            }
            Service.gI().chatJustForMe(master, this, "Sư phụ ơi cho con đậu thần");
            UseItem.gI().eatPea(master);
            lastTimeAskPea = System.currentTimeMillis();
        }
    }

    private int countTTNL;

    private boolean useSkill3() {
        try {
            playerSkill.skillSelect = getSkill(3);
            if (playerSkill.skillSelect.skillId == -1) {
                return false;
            }
            switch (this.playerSkill.skillSelect.template.id) {
                case Skill.THAI_DUONG_HA_SAN:
                    if (SkillService.gI().canUseSkillWithCooldown(this) && SkillService.gI().canUseSkillWithMana(this)) {
                        SkillService.gI().useSkill(this, null, null, -1, null);
                        Service.gI().chatJustForMe(master, this, "Bất ngờ chưa ông già");
                        return true;
                    }
                    return false;
                case Skill.TAI_TAO_NANG_LUONG:
                    if (this.effectSkill.isCharging && this.countTTNL < Util.nextInt(
                            PetConfig.getInt("pet.ai.regenChargeMin", 3, 1, 100),
                            PetConfig.getInt("pet.ai.regenChargeMax", 5, 1, 100))) {
                        this.countTTNL++;
                        return true;
                    }
                    if (SkillService.gI().canUseSkillWithCooldown(this) && SkillService.gI().canUseSkillWithMana(this)
                            && (this.nPoint.getCurrPercentHP() <= PetConfig.getInt("pet.ai.regenThresholdPercent", 20, 0, 100)
                            || this.nPoint.getCurrPercentMP() <= PetConfig.getInt("pet.ai.regenThresholdPercent", 20, 0, 100))) {
                        SkillService.gI().useSkill(this, null, null, -1, null);
                        this.countTTNL = 0;
                        return true;
                    }
                    return false;
                case Skill.KAIOKEN:
                    if (SkillService.gI().canUseSkillWithCooldown(this) && SkillService.gI().canUseSkillWithMana(this)) {

                        mobAttack = this.findMobAttack();
                        playerAttack = this.findPlayerAttack();
                        if (playerAttack != null) {
                            mobAttack = null;
                            int dis = Util.getDistance(this, playerAttack);
                            if (dis > PetConfig.getInt("pet.ai.meleeRange", 50, 1, 10_000)) {
                                PlayerService.gI().playerMove(this, playerAttack.location.x, playerAttack.location.y);
                            } else {
                                if (SkillService.gI().canUseSkillWithCooldown(this) && SkillService.gI().canUseSkillWithMana(this)) {
                                    PlayerService.gI().playerMove(this, playerAttack.location.x + Util.nextInt(-20, 20), playerAttack.location.y);
                                }
                            }
                        } else if (mobAttack == null) {
                            return false;
                        }
                        if (mobAttack != null) {
                            int dis = Util.getDistance(this, mobAttack);
                            if (dis > PetConfig.getInt("pet.ai.meleeRange", 50, 1, 10_000)) {
                                PlayerService.gI().playerMove(this, mobAttack.location.x, mobAttack.location.y);
                            } else {
                                if (SkillService.gI().canUseSkillWithCooldown(this) && SkillService.gI().canUseSkillWithMana(this)) {
                                    PlayerService.gI().playerMove(this, mobAttack.location.x + Util.nextInt(-20, 20), mobAttack.location.y);
                                }
                            }
                        }

                        SkillService.gI().useSkill(this, playerAttack, mobAttack, -1, null);
                        getSkill(1).lastTimeUseThisSkill = System.currentTimeMillis();
                        return true;
                    }
                    return false;
                default:
                    return useConfiguredSkill(this.playerSkill.skillSelect);
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean useSkill4() {
        try {
            this.playerSkill.skillSelect = getSkill(4);
            if (this.playerSkill.skillSelect.skillId == -1) {
                return false;
            }
            switch (this.playerSkill.skillSelect.template.id) {
                case Skill.BIEN_KHI:
                    if (!this.effectSkill.isMonkey && SkillService.gI().canUseSkillWithCooldown(this) && SkillService.gI().canUseSkillWithMana(this)) {
                        SkillService.gI().useSkill(this, null, null, -1, null);
                        return true;
                    }
                    return false;
                case Skill.KHIEN_NANG_LUONG:
                    if (!this.effectSkill.isShielding && SkillService.gI().canUseSkillWithCooldown(this) && SkillService.gI().canUseSkillWithMana(this)) {
                        SkillService.gI().useSkill(this, null, null, -1, null);
                        return true;
                    }
                    return false;
                case Skill.DE_TRUNG:
                    if (this.mobMe == null && SkillService.gI().canUseSkillWithCooldown(this) && SkillService.gI().canUseSkillWithMana(this)) {
                        SkillService.gI().useSkill(this, null, null, -1, null);
                        return true;
                    }
                    return false;
                default:
                    return useConfiguredSkill(this.playerSkill.skillSelect);
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean useSkill5() {
        try {
            Skill skill = this.playerSkill.skillSelect = getSkill(5);
            if (skill == null || skill.skillId == -1) {
                return false;
            }
            return useConfiguredSkill(skill);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean useConfiguredSkill(Skill skill) {
        if (skill == null || skill.template == null
                || !SkillService.gI().canUseSkillWithCooldown(this)
                || !SkillService.gI().canUseSkillWithMana(this)) {
            return false;
        }
        this.playerSkill.skillSelect = skill;
        if (skill.template.type == 2) {
            return SkillService.gI().useSkill(this, master, null, -1, null);
        }
        if (skill.template.type == 3) {
            return SkillService.gI().useSkill(this, null, null, -1, null);
        }
        Player target = findPlayerAttack();
        Mob targetMob = target == null ? findMobAttack() : null;
        if (target == null && targetMob == null) {
            return false;
        }
        if (skill.template.type == 4 && this.newSkill == null) {
            return false;
        }
        return SkillService.gI().useSkill(this, target, targetMob, -1, null);
    }

    private long lastTimeIncreasePoint;

    private void increasePoint() {
        if (this.nPoint != null && Util.canDoWithTime(lastTimeIncreasePoint,
                PetConfig.getInt("pet.ai.autoStatIntervalMs", 0, 0, Integer.MAX_VALUE))) {
            int attempts = PetConfig.getInt("pet.ai.autoStatAttempts", 20, 0, 10_000);
            for (int i = 0; i < attempts; i++) {
                this.nPoint.increasePoint((byte) Util.nextInt(0, 4), (short) 1, false);
            }
            lastTimeIncreasePoint = System.currentTimeMillis();
        }
    }

    public void followMaster() {
        if (this.isDie() || effectSkill.isHaveEffectSkill()) {
            return;
        }
        switch (this.status) {
            case ATTACK:
                if ((mobAttack != null && Util.getDistance(this, master) <= 1000)) {
                    break;
                }
            case FOLLOW:
            case PROTECT:
                followMaster(PetConfig.getInt("pet.ai.followCombatDistance", 500, 1, 10_000));
                break;
        }
    }

    private void followMaster(int dis) {
        int mX = master.location.x;
        int mY = master.location.y;
        int disX = this.location.x - mX;
        if (Math.sqrt(Math.pow(mX - this.location.x, 2) + Math.pow(mY - this.location.y, 2)) >= dis || disX < 50) {
            if (disX < 0) {
                this.location.x = mX - 50;
            } else {
                this.location.x = mX + 50;
            }
            this.location.y = mY;
            PlayerService.gI().playerMove(this, this.location.x, this.location.y);
        }
    }

    public short getAvatar() {
        switch (this.typePet) {
            case 1:
                return 297;
            case 2:
                return 946;
            case 3:
                return 1422;
            case 4:
                return 876;
            default:
                return PET_ID[3][this.gender];
        }
    }

    @Override
    public short getHead() {
        if (effectSkill != null && effectSkill.isBinh) {
            return idOutfitMafuba[effectSkill.typeBinh][0];
        }
        if (effectSkill != null && effectSkill.isStone) {
            return 454;
        }
        if (effectSkill != null && effectSkill.isHalloween) {
            return idOutfitHalloween[effectSkill.idOutfitHalloween][this.gender][0];
        }
        if (effectSkill != null && effectSkill.isMonkey) {
            return (short) ConstPlayer.HEADMONKEY[effectSkill.levelMonkey - 1];
        } else if (effectSkill != null && effectSkill.isSocola) {
            return 412;
        } else if (this.typePet == 1) {
            return 297;
        } else if (this.typePet == 2) {
            return 946;
        } else if (this.typePet == 3) {
            return 1422;
        } else if (this.typePet == 4) {
            return 876;
        } else if (inventory.itemsBody.get(5).isNotNullItem()) {
            int part = inventory.itemsBody.get(5).template.head;
            if (part != -1) {
                return (short) part;
            }
        }
        if (this.nPoint.power < 1500000) {
            return PET_ID[this.gender][0];
        } else {
            return PET_ID[3][this.gender];
        }
    }

    @Override
    public short getBody() {
        if (effectSkill != null && effectSkill.isBinh) {
            return idOutfitMafuba[effectSkill.typeBinh][1];
        }
        if (effectSkill != null && effectSkill.isStone) {
            return 455;
        }
        if (effectSkill != null && effectSkill.isHalloween) {
            return idOutfitHalloween[effectSkill.idOutfitHalloween][this.gender][1];
        }
        if (effectSkill != null && effectSkill.isMonkey) {
            return 193;
        } else if (effectSkill != null && effectSkill.isSocola) {
            return 413;
        } else if (this.typePet == 1 && !this.isTransform) {
            return 298;
        } else if (this.typePet == 2 && !this.isTransform) {
            return 947;
        } else if (this.typePet == 3 && !this.isTransform) {
            return 1423;
        } else if (this.typePet == 4) {
            return 877;
        } else if (inventory.itemsBody.get(5).isNotNullItem()) {
            int body = inventory.itemsBody.get(5).template.body;
            if (body != -1) {
                return (short) body;
            }
        }
        if (inventory.itemsBody.get(0).isNotNullItem()) {
            return inventory.itemsBody.get(0).template.part;
        }
        if (this.nPoint.power < 1500000) {
            return PET_ID[this.gender][1];
        } else {
            return (short) (gender == ConstPlayer.NAMEC ? 59 : 57);
        }
    }

    @Override
    public short getLeg() {
        if (effectSkill != null && effectSkill.isBinh) {
            return idOutfitMafuba[effectSkill.typeBinh][2];
        }
        if (effectSkill != null && effectSkill.isStone) {
            return 456;
        }
        if (effectSkill != null && effectSkill.isHalloween) {
            return idOutfitHalloween[effectSkill.idOutfitHalloween][this.gender][2];
        }
        if (effectSkill != null && effectSkill.isMonkey) {
            return 194;
        } else if (effectSkill != null && effectSkill.isSocola) {
            return 414;
        } else if (this.typePet == 1 && !this.isTransform) {
            return 299;
        } else if (this.typePet == 2 && !this.isTransform) {
            return 948;
        } else if (this.typePet == 3 && !this.isTransform) {
            return 1424;
        } else if (this.typePet == 4) {
            return 878;
        } else if (inventory.itemsBody.get(5).isNotNullItem()) {
            int leg = inventory.itemsBody.get(5).template.leg;
            if (leg != -1) {
                return (short) leg;
            }
        }
        if (inventory.itemsBody.get(1).isNotNullItem()) {
            return inventory.itemsBody.get(1).template.part;
        }

        if (this.nPoint.power < 1500000) {
            return PET_ID[this.gender][2];
        } else {
            return (short) (gender == ConstPlayer.NAMEC ? 60 : 58);
        }
    }

    private Player findPlayerAttack() {
        List<Player> playersMap = zone.getHumanoids();
        int dis = PetConfig.getInt("pet.ai.attackRange", 300, 1, 10_000);
        Player plAtt = null;

        for (int i = playersMap.size() - 1; i >= 0; i--) {
            Player pl = playersMap.get(i);
            if (!cantAttack(pl)) {
                int d = Util.getDistance(this, pl);
                if (d <= dis) {
                    dis = d;
                    plAtt = pl;
                }
            }
        }

        return plAtt;
    }

    private boolean cantAttack(Player player) {
        return player == null || player.location == null || player.isDie()
                || Util.getDistance(this, player) > PetConfig.getInt("pet.ai.followCombatDistance", 500, 1, 10_000)
                || this.equals(player) || player.equals(master)
                || !PetConfig.isTypeAllowed("pet.ai.playerAttackAllowedTypes", this.typePet, 2, 4)
                || (!temporaryEnemies.contains(player) && !master.temporaryEnemies.contains(player))
                || (!SkillService.gI().canAttackPlayer(this, player));
    }

    private Mob findMobAttack() {
        int dis = PetConfig.getInt("pet.ai.attackRange", 300, 1, 10_000);
        Mob mobAtt = null;
        for (Mob mob : zone.mobs) {
            if (mob.isDie()) {
                continue;
            }
            int d = Util.getDistance(this, mob);
            if (d <= dis) {
                dis = d;
                mobAtt = mob;
            }
        }
        return mobAtt;
    }

    private void updatePower() {
        if (PetConfig.isAutoUnlockSkill() && this.playerSkill != null) {
            switch (this.playerSkill.getSizeSkill()) {
                case 1:
                    if (this.nPoint.power >= PetConfig.getSkillUnlockPower(2)) {
                        openSkill2();
                    }
                    break;
                case 2:
                    if (this.nPoint.power >= PetConfig.getSkillUnlockPower(3)) {
                        openSkill3();
                    }
                    break;
                case 3:
                    if (this.nPoint.power >= PetConfig.getSkillUnlockPower(4)) {
                        openSkill4();
                    }
                    break;
                case 4:
                    if (PetConfig.isTypeAllowed("pet.skill.fifthAllowedTypes", this.typePet, 2, 3, 4)
                            && this.nPoint.power >= PetConfig.getSkillUnlockPower(5)) {
                        openSkill5();
                    }
                    break;
            }
        }
    }

    public void openSkill2() {
        setRandomSkillFromPool(2);
    }

    public void openSkill3() {
        setRandomSkillFromPool(3);
    }

    public void openSkill4() {
        setRandomSkillFromPool(4);
    }

    public void openSkill5() {
        setRandomSkillFromPool(5);
    }

    private void setRandomSkillFromPool(int skillSlot) {
        int listIndex = skillSlot - 1;
        if (this.playerSkill == null || listIndex < 0 || listIndex >= this.playerSkill.skills.size()) {
            return;
        }
        Skill current = this.playerSkill.skills.get(listIndex);
        int currentTemplateId = current != null && current.template != null ? current.template.id : -1;
        int[] configured = PetConfig.getSkillPool(skillSlot);
        java.util.List<Skill> candidates = new java.util.ArrayList<>();
        for (int templateId : configured) {
            Skill candidate = SkillUtil.createSkill(templateId, 1);
            if (candidate != null && (configured.length == 1 || templateId != currentTemplateId)) {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) {
            for (int templateId : configured) {
                Skill candidate = SkillUtil.createSkill(templateId, 1);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
        }
        if (!candidates.isEmpty()) {
            Skill selected = candidates.get(Util.nextInt(candidates.size()));
            if (skillSlot == 2) {
                selected.coolDown = PetConfig.getAttackSkillCooldownMs();
            }
            this.playerSkill.skills.set(listIndex, selected);
        }
    }

    private Skill getSkill(int indexSkill) {
        return this.playerSkill.skills.get(indexSkill - 1);
    }

    public void transform() {
        if (this.typePet == 1) {
            this.isTransform = !this.isTransform;
            Service.gI().Send_Caitrang(this);
            Service.gI().chat(this, "Bố Mày Là Bư Nè !! Bư..Bư..Bư..Ma..Nhân..Bư....");
        }
        if (this.typePet == 2) {
            this.isTransform = !this.isTransform;
            Service.gI().Send_Caitrang(this);
            Service.gI().chat(this, "Tao là thần");
        }
        if (this.typePet == 2) {
            this.isTransform = !this.isTransform;
            Service.gI().Send_Caitrang(this);
            Service.gI().chat(this, "Chúng mày quỳ xuống");
        }
        if (this.typePet == 4) {
            this.isTransform = !this.isTransform;
            Service.gI().Send_Caitrang(this);
            Service.gI().chat(this, "Hỡi nhân loại thấp kém! Hãy chiêm ngưỡng vẻ đẹp của ta!");
        }
    }

    public long lastTimeAskAttack;

    public boolean canAttack() {
        if (this.master.isPl() && this.master.doesNotAttack && this.master.charms.tdDeTu < System.currentTimeMillis()) {
            if (Util.canDoWithTime(lastTimeAskAttack, 10000)) {
                Service.gI().chatJustForMe(master, this, this.typePet == 4 ? "Sao ngươi không đánh đi?" : "Sao sư phụ không đánh đi?");
                lastTimeAskAttack = System.currentTimeMillis();
            }
            return false;
        }
        return true;
    }

    public void petSay(Player player) {
        if (this.typePet == 4) {
            if (Util.canDoWithTime(lastTimeChat, indexChat == 0 ? 15000 : 1500)) {
                String[] chat = {
                    "Ta chính là thế giới",
                    "Ta chính là công lí",
                    "Hãy chiêm ngưỡng vẻ đẹp của ta! Hỡi con người",
                    "Sức mạnh to lớn nằm trong cơ thể bất tử",
                    "Ta sẽ đem công lí tới toàn bộ vũ trụ này"
                };
                Service.gI().chat(this, chat[indexChat]);
                indexChat = (indexChat + 1) % chat.length;
                lastTimeChat = System.currentTimeMillis();
            }
        } else {
            if (Util.canDoWithTime(lastTimeChat, indexChat == 0 ? 15000 : 1500)) {
                String[] chat = {
                    "Mày chán sống rồi à " + player.name + "?",
                    "Mày muốn chết đúng không?",
                    "Ngày này năm sau",
                    "Tao sẽ nhớ uống thật nhiều nước",
                    "Để đái vào mộ mày"
                };
                Service.gI().chat(this, chat[indexChat]);
                indexChat = (indexChat + 1) % chat.length;
                lastTimeChat = System.currentTimeMillis();
            }
        }
    }

    @Override
    public void dispose() {
        if (zone != null) {
            ChangeMapService.gI().exitMap(this);
        }
        this.mobAttack = null;
        this.playerAttack = null;
        this.master = null;
        super.dispose();
    }
}
