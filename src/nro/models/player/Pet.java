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

    private static final short ARANGE_CAN_ATTACK = 300;
    private static final short ARANGE_ATT_SKILL1 = 50;

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

    private static final int TIME_WAIT_AFTER_UNFUSION = 5000;
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
        if (Util.canDoWithTime(lastTimeUnfusion, TIME_WAIT_AFTER_UNFUSION)) {
            if (porata) {
                master.fusion.typeFusion = ConstPlayer.HOP_THE_GOGETA;
            } else {
                master.fusion.lastTimeFusion = System.currentTimeMillis();
                master.fusion.typeFusion = ConstPlayer.LUONG_LONG_NHAT_THE;
                ItemTimeService.gI().sendItemTime(master, master.gender == ConstPlayer.NAMEC ? 3901 : 3790,
                        Fusion.TIME_FUSION / 1000);
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
                    + TimeUtil.getTimeLeft(lastTimeUnfusion, TIME_WAIT_AFTER_UNFUSION / 1000) + " nữa");
        }
    }

    public void fusion2(boolean porata) {
        if (this.isDie()) {
            Service.gI().sendThongBao(master, "Yêu cầu phải có đệ tử và đệ tử còn sống");
            return;
        }
        if (Util.canDoWithTime(lastTimeUnfusion, TIME_WAIT_AFTER_UNFUSION)) {
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
                    + TimeUtil.getTimeLeft(lastTimeUnfusion, TIME_WAIT_AFTER_UNFUSION / 1000) + " nữa");
        }
    }
    public void fusion3(boolean porata) {
        if (this.isDie()) {
            Service.gI().sendThongBao(master, "Yêu cầu phải có đệ tử và đệ tử còn sống");
            return;
        }
        if (Util.canDoWithTime(lastTimeUnfusion, TIME_WAIT_AFTER_UNFUSION)) {
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
                    + TimeUtil.getTimeLeft(lastTimeUnfusion, TIME_WAIT_AFTER_UNFUSION / 1000) + " nữa");
        }
    }

    public void fusion(boolean porata) {
        if (this.isDie()) {
            Service.gI().sendThongBao(master, "Yêu cầu phải có đệ tử và đệ tử còn sống");
            return;
        }
        if (Util.canDoWithTime(lastTimeUnfusion, TIME_WAIT_AFTER_UNFUSION)) {
            if (porata) {
                master.fusion.typeFusion = ConstPlayer.HOP_THE_PORATA;
            } else {
                master.fusion.lastTimeFusion = System.currentTimeMillis();
                master.fusion.typeFusion = ConstPlayer.LUONG_LONG_NHAT_THE;
                ItemTimeService.gI().sendItemTime(master, master.gender == ConstPlayer.NAMEC ? 3901 : 3790, Fusion.TIME_FUSION / 1000);
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
                    + TimeUtil.getTimeLeft(lastTimeUnfusion, TIME_WAIT_AFTER_UNFUSION / 1000) + " nữa");
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
                    if (System.currentTimeMillis() - lastTimeDie > 120000) {
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
                        followMaster(60);
                        break;

                    case PROTECT:
                        if (useSkill3() || useSkill4() || useSkill5()) {
                            break;
                        }

                        playerAttack = findPlayerAttack();
                        if (playerAttack != null) {
                            if ((this.typePet == 2 || this.typePet == 4 || this.typePet == 5)
                                    && Util.isTrue(1, 5)
                                    && playerAttack.nPoint.hp < 1_000_000_000
                                    && !playerAttack.nPoint.islinhthuydanhbac
                                    && !playerAttack.isBoss) {
                                playerAttack.setDie(this);
                                Service.gI().chat(this, "HAKAI " + playerAttack.name + "!");
                                Service.gI().sendThongBao(playerAttack, "Bạn đã bị Hakai!");
                            } else {
                                petSay(playerAttack);
                            }

                            int disToPlayer = Util.getDistance(this, playerAttack);
                            if (disToPlayer <= ARANGE_ATT_SKILL1) {
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
                            if (disToMob <= ARANGE_ATT_SKILL1) {
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
                            if (disToPlayer <= ARANGE_ATT_SKILL1) {
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
                            if (disToMob <= ARANGE_ATT_SKILL1) {
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
        if (Util.canDoWithTime(lastTimeAskPea, 10000)) {
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
                    if (this.effectSkill.isCharging && this.countTTNL < Util.nextInt(3, 5)) {
                        this.countTTNL++;
                        return true;
                    }
                    if (SkillService.gI().canUseSkillWithCooldown(this) && SkillService.gI().canUseSkillWithMana(this)
                            && (this.nPoint.getCurrPercentHP() <= 20 || this.nPoint.getCurrPercentMP() <= 20)) {
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
                            if (dis > ARANGE_ATT_SKILL1) {
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
                            if (dis > ARANGE_ATT_SKILL1) {
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
                    return false;
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
                    return false;
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

            this.playerSkill.skillSelect = skill;

            boolean canUse = SkillService.gI().canUseSkillWithCooldown(this)
                    && SkillService.gI().canUseSkillWithMana(this);

            if (!canUse || this.newSkill == null) {
                return false;
            }

            int skillId = skill.template.id;

            switch (skillId) {
                case Skill.SUPER_KAME, Skill.LIEN_HOAN_CHUONG, Skill.MA_PHONG_BA -> {
                    short dx = (short) this.location.x;
                    short dy = (short) this.location.y;
                    short x = dx;
                    short y = dy;
                    byte dir = 1;

                    Player target = this.zone.findNearestPlayer(this);
                    Mob mobTarget = this.zone.findNearestMob(this);

                    if (target != null) {
                        x = (short) target.location.x;
                        y = (short) target.location.y;
                    } else if (mobTarget != null) {
                        x = (short) mobTarget.location.x;
                        y = (short) mobTarget.location.y;
                    } else {
                        return false;
                    }

                    dir = (byte) (dx > x ? -1 : 1);

                    this.newSkill.setSkillSpecial(dir, dx, dy, x, y);

                    SkillService.gI().newSkillNotFocus(this, 20);
                    SkillService.gI().affterUseSkill(this, skillId);

                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private long lastTimeIncreasePoint;

    private void increasePoint() {
        if (this.nPoint != null && Util.canDoWithTime(lastTimeIncreasePoint, 0)) {
            for (int i = 0; i < 20; i++) {
                this.nPoint.increasePoint((byte) Util.nextInt(0, 4), (short) 1);
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
                followMaster(500);
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
        int dis = ARANGE_CAN_ATTACK;
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
        return player == null || player.location == null || player.isDie() || Util.getDistance(this, player) > 500
                || this.equals(player) || (player.equals(master) || this.typePet != 5 && this.typePet != 2 && this.typePet != 4)
                || (!temporaryEnemies.contains(player) && !master.temporaryEnemies.contains(player))
                || (!SkillService.gI().canAttackPlayer(this, player));
    }

    private Mob findMobAttack() {
        int dis = ARANGE_CAN_ATTACK;
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
        if (this.playerSkill != null) {
            switch (this.playerSkill.getSizeSkill()) {
                case 1:
                    if (this.nPoint.power >= 150000000) {
                        openSkill2();
                    }
                    break;
                case 2:
                    if (this.nPoint.power >= 1500000000) {
                        openSkill3();
                    }
                    break;
                case 3:
                    if (this.nPoint.power >= 20000000000L) {
                        openSkill4();
                    }
                    break;
                case 4:
                    if ((this.typePet == 2 || this.typePet == 3 || this.typePet == 4) && this.nPoint.power >= 40000000000L) {
                        openSkill5();
                    }
                    break;
            }
        }
    }

    public void openSkill2() {
        Skill currentSkill = this.playerSkill.skills.get(1);
        Skill skill = null;

        int[] tiLe = {33, 33, 34};
        byte[] skills = {Skill.KAMEJOKO, Skill.MASENKO, Skill.ANTOMIC};

        int totalTiLe = 0;
        for (int t : tiLe) {
            totalTiLe += t;
        }

        int rd = Util.nextInt(1, totalTiLe);

        int accumulatedTiLe = 0;
        for (int i = 0; i < tiLe.length; i++) {
            accumulatedTiLe += tiLe[i];
            if (rd <= accumulatedTiLe) {
                skill = SkillUtil.createSkill(skills[i], 1);
                break;
            }
        }

        while (currentSkill != null && skill != null && skill.skillId == currentSkill.skillId) {
            rd = Util.nextInt(1, totalTiLe);
            accumulatedTiLe = 0;
            for (int i = 0; i < tiLe.length; i++) {
                accumulatedTiLe += tiLe[i];
                if (rd <= accumulatedTiLe) {
                    skill = SkillUtil.createSkill(skills[i], 1);
                    break;
                }
            }
        }

        skill.coolDown = 1000;
        this.playerSkill.skills.set(1, skill);
    }

    public void openSkill3() {
        Skill skill = null;

        int[] tiLe = {30, 40, 30};
        byte[] skills = {Skill.THAI_DUONG_HA_SAN, Skill.TAI_TAO_NANG_LUONG, Skill.KAIOKEN};

        int totalTiLe = 0;
        for (int t : tiLe) {
            totalTiLe += t;
        }
        int rd = Util.nextInt(1, totalTiLe);

        int accumulatedTiLe = 0;
        for (int i = 0; i < tiLe.length; i++) {
            accumulatedTiLe += tiLe[i];
            if (rd <= accumulatedTiLe) {
                skill = SkillUtil.createSkill(skills[i], 1);
                break;
            }
        }

        this.playerSkill.skills.set(2, skill);
    }

    public void openSkill4() {
        Skill skill = null;
        int tiLeBienKhi = 10;
        int tiLeDeTrung = 70;
        int tiLeKNL = 20;

        int rd = Util.nextInt(1, 100);
        if (rd <= tiLeBienKhi) {
            skill = SkillUtil.createSkill(Skill.BIEN_KHI, 1);
        } else if (rd <= tiLeBienKhi + tiLeDeTrung) {
            skill = SkillUtil.createSkill(Skill.DE_TRUNG, 1);
        } else if (rd <= tiLeBienKhi + tiLeDeTrung + tiLeKNL) {
            skill = SkillUtil.createSkill(Skill.KHIEN_NANG_LUONG, 1);
        }
        this.playerSkill.skills.set(3, skill);
    }

    public void openSkill5() {
        Skill skill = null;

        switch (this.gender) {
            case 0 ->
                skill = SkillUtil.createSkill(Skill.SUPER_KAME, 1);
            case 1 ->
                skill = SkillUtil.createSkill(Skill.MA_PHONG_BA, 1);
            case 2 ->
                skill = SkillUtil.createSkill(Skill.LIEN_HOAN_CHUONG, 1);
            default -> {
                return;
            }
        }

        if (skill != null) {
            this.playerSkill.skills.set(4, skill);
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
