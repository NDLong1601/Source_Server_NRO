package nro.models.boss.ma_vuong_picolo;


import nro.models.boss.Boss;
import nro.models.boss.BossData;
import nro.models.boss.BossID;
import nro.models.consts.BossStatus;
import nro.models.consts.ConstPlayer;
import static nro.models.consts.BossType.PHOBAN;
import nro.models.boss.Boss_Manager.OtherBossManager;
import nro.models.network.Message;
import java.io.IOException;
import nro.models.services_dungeon.SuperDivineWaterService;
import nro.models.map.Zone;
import nro.models.mob.Mob;
import nro.models.player.Player;
import nro.models.services.EffectSkillService;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import nro.models.services.SkillService;
import nro.models.skill.Skill;
import nro.models.map.service.ChangeMapService;
import nro.models.utils.SkillUtil;
import nro.models.utils.Util;

public class Pocolo extends Boss {

    private final Player playerCall;
    private boolean revivedMobs;
    private boolean ttnl;
    private long lastTimeJoin;
    private boolean isLaze;
    private long lastTimeLaze;

    private final String text[] = {"Không xong rồi, không xong rồi", "Nguy to cho thằng nhóc rồi"};
    private long lastTimeChat;
    private long lastTimeMove;
    private int indexChat = 0;

    public Pocolo(Zone zone, Player player, int dame, int hp) throws Exception {
        super(PHOBAN, BossID.POCOLO, new BossData(
                "Ma vương Pôcôlô",
                ConstPlayer.NAMEC,
                new short[]{739, 740, 741, -1, -1, -1},
                dame,
                new int[]{hp},
                new int[]{146},
                new int[][]{
                    {Skill.DEMON, 7, 1000},
                    {Skill.MASENKO, 1, 1000},
                    {Skill.MASENKO, 2, 1000},
                    {Skill.MASENKO, 3, 1000},
                    {Skill.MASENKO, 4, 1000},
                    {Skill.MASENKO, 5, 1000},
                    {Skill.MASENKO, 6, 1000},
                    {Skill.MASENKO, 7, 1000},
                    {Skill.KAMEJOKO, 1, 1000},
                    {Skill.KAMEJOKO, 2, 1000},
                    {Skill.KAMEJOKO, 3, 1000},
                    {Skill.KAMEJOKO, 4, 1000},
                    {Skill.KAMEJOKO, 5, 1000},
                    {Skill.KAMEJOKO, 6, 1000},
                    {Skill.KAMEJOKO, 7, 1000},
                    {Skill.ANTOMIC, 1, 1000},
                    {Skill.ANTOMIC, 2, 1000},
                    {Skill.ANTOMIC, 3, 1000},
                    {Skill.ANTOMIC, 4, 1000},
                    {Skill.ANTOMIC, 5, 1000},
                    {Skill.ANTOMIC, 6, 1000},
                    {Skill.ANTOMIC, 7, 1000},
                    {Skill.TAI_TAO_NANG_LUONG, 7, 15000},},//skill
                new String[]{"|-1|Được! Mi muốn chết thì ta cho chết!"},
                new String[]{"|-1|Khí công pháo"},
                new String[]{"|-1|Hâyaaaa"},
                60
        ));
        this.zone = zone;
        this.playerCall = player;
    }

    @Override
    public synchronized int injured(Player plAtt, long damage, boolean piercing, boolean isMobAttack) {
        if (!this.isDie()) {
            if (!piercing && Util.isTrue(10, 1000)) {
                this.chat("Xí hụt");
                return 0;
            }

            if (plAtt != null && plAtt.idNRNM != -1) {
                return 1;
            }

            if (!piercing && effectSkill.isShielding) {
                if (damage > nPoint.hpMax) {
                    EffectSkillService.gI().breakShield(this);
                }
                damage = 1;
            }
            if (damage >= this.nPoint.hp) {
                this.laze();
                damage = 0;
            }

            this.nPoint.subHP(damage);

            if (isDie()) {
                this.setDie(plAtt);
                die(plAtt);
            }

            return (int) damage;
        } else {
            return 0;
        }
    }

    public void laze() {
        if (!this.isLaze) {
            this.effectSkill.isCharging = false;
            ChangeMapService.gI().changeMap(this, this.zone, this.location.x, this.location.y);
            this.chat("Xem đây");
            Message msg;
            try {
                msg = new Message(-45);
                msg.writer().writeByte(4);
                msg.writer().writeInt((int) this.id);
                msg.writer().writeShort(83);
                msg.writer().writeShort(3000);
                Service.gI().sendMessAllPlayerInMap(this, msg);
                msg.cleanup();
            } catch (Exception e) {
            }
            this.isLaze = true;
            this.lastTimeLaze = System.currentTimeMillis();
        }
    }

    @Override
    public void autoLeaveMap() {
        if (Util.canDoWithTime(lastTimeJoin, 900000)) {
            this.leaveMap();
        }
        if (this.bossStatus == BossStatus.ACTIVE) {
            this.mc();
        }
        if (this.isLaze && !playerCall.winSTT && Util.canDoWithTime(lastTimeLaze, 3000)) {
            playerCall.winSTT = true;
            playerCall.lastTimeWinSTT = System.currentTimeMillis();
            Message msg;
            try {
                msg = new Message(-60);
                msg.writer().writeInt((int) this.id); //id pem
                msg.writer().writeByte(83); //skill pem
                msg.writer().writeByte(1); //số người pem
                msg.writer().writeInt((int) playerCall.id); //id ăn pem
                msg.writer().writeByte(1); //read continue
                msg.writer().writeByte(1); //type skill
                msg.writer().writeInt(1_000_000_000); //dame ăn
                msg.writer().writeBoolean(true); //is die
                msg.writer().writeBoolean(this.nPoint.isCrit); //crit
                Service.gI().sendMessAllPlayerInMap(this, msg);
                msg.cleanup();
            } catch (IOException e) {
            }
            playerCall.setDie();
            EffectSkillService.gI().setPKSTT(playerCall, 60000);
            this.chat("Hâyaaaa");
            this.changeStatus(BossStatus.DIE);
        }
    }

    @Override
    public void afk() {
        if (Util.canDoWithTime(lastTimeJoin, 3000)) {
            if (!ttnl) {
                this.playerSkill.skillSelect = this.playerSkill.skills.get(22);
                SkillService.gI().useSkill(this, null, null, -1, null);
                this.chat("Hồi sinh đi các con của ta.");
                ttnl = true;
            }
            if (Util.canDoWithTime(lastTimeJoin, 5000)) {
                if (!revivedMobs) {
                    SuperDivineWaterService.gI().init(this.zone, this.playerCall);
                    revivedMobs = true;
                    this.chat("Các con của ta hãy tiêu diệt nó.");
                }
                if (revivedMobs) {
                    boolean allCharactersDead = true;
                    for (Mob mob : this.zone.mobs) {
                        if (!mob.isDie()) {
                            allCharactersDead = false;
                            break;
                        }
                    }
                    if (allCharactersDead) {
                        Player pl = getPlayerAttack();
                        if (pl == null || pl.isDie()) {
                            return;
                        }
                        Service.gI().setPos(this, pl.location.x + (Util.getOne(-1, 1) * Util.nextInt(50, 100)), 336);
                        this.moveToPlayer(pl);
                        this.changeStatus(BossStatus.CHAT_S);
                    }
                }
            }
        }
    }

    @Override
    public void chatM() {
        if (this.isLaze) {
            return;
        }
        if (this.typePk == ConstPlayer.NON_PK) {
            return;
        }
        if (this.data[this.currentLevel].getTextM().length == 0) {
            return;
        }
        if (!Util.canDoWithTime(this.lastTimeChatM, this.timeChatM)) {
            return;
        }
        String textChat = this.data[this.currentLevel].getTextM()[Util.nextInt(0, this.data[this.currentLevel].getTextM().length - 1)];
        int prefix = Integer.parseInt(textChat.substring(1, textChat.lastIndexOf("|")));
        textChat = textChat.substring(textChat.lastIndexOf("|") + 1);
        this.chat(prefix, textChat);
        this.lastTimeChatM = System.currentTimeMillis();
        this.timeChatM = 5000;
    }

    @Override
    public void attack() {
        if (isLaze) {
            return;
        }
        if (Util.canDoWithTime(this.lastTimeAttack, 100) && this.typePk == ConstPlayer.PK_ALL) {
            this.lastTimeAttack = System.currentTimeMillis();
            try {
                Player pl = getPlayerAttack();
                if (pl == null || pl.isDie()) {
                    return;
                }
                this.playerSkill.skillSelect = this.playerSkill.skills.get(Util.nextInt(0, 22));
                if (Util.getDistance(this, pl) <= this.getRangeCanAttackWithSkillSelect()) {
                    if (Util.isTrue(5, 20)) {
                        if (SkillUtil.isUseSkillChuong(this)) {
                            this.moveTo(pl.location.x + (Util.getOne(-1, 1) * Util.nextInt(20, 200)), pl.location.y);
                        } else {
                            this.moveTo(pl.location.x + (Util.getOne(-1, 1) * Util.nextInt(10, 40)), pl.location.y);
                        }
                    }
                    if (this.nPoint.getDameAttack(false) >= pl.nPoint.hp) {
                        this.laze();
                        return;
                    }
                    SkillService.gI().useSkill(this, pl, null, -1, null);
                    checkPlayerDie(pl);
                } else {
                    if (Util.isTrue(1, 2)) {
                        this.moveToPlayer(pl);
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void reward(Player plKill) {
    }

    @Override
    public void joinMap() {
        ChangeMapService.gI().changeMap(this, this.zone, 820, 36);
        this.moveTo(820, 336);
        this.lastTimeJoin = System.currentTimeMillis();
        this.nPoint.mp = 100;
        this.nPoint.mpMax = 2_147_483_647;
        this.changeStatus(BossStatus.AFK);
    }

    @Override
    public void die(Player plKill) {
        if (plKill != null) {
            reward(plKill);
        }
        this.changeStatus(BossStatus.DIE);
    }

    @Override
    public void leaveMap() {
        ChangeMapService.gI().spaceShipArrive(this, (byte) 2, ChangeMapService.DEFAULT_SPACE_SHIP);
        ChangeMapService.gI().exitMap(this);
        this.lastZone = null;
        this.lastTimeRest = System.currentTimeMillis();
        this.changeStatus(BossStatus.REST);
        OtherBossManager.gI().removeBoss(this);
        this.dispose();
    }

    public void mc() {
        Player mc = zone.getNpc();
        if (mc != null) {
            if (Util.canDoWithTime(lastTimeChat, 3000)) {
                String textchat = text[indexChat];
                Service.gI().chat(mc, textchat);
                indexChat++;
                if (indexChat == text.length) {
                    indexChat = 0;
                    lastTimeChat = System.currentTimeMillis() + 7000;
                } else {
                    lastTimeChat = System.currentTimeMillis();
                }
            }

            if (Util.canDoWithTime(lastTimeMove, 15000)) {
                if (Util.isTrue(2, 3)) {
                    int x = this.location.x + Util.nextInt(-100, 100);
                    int y = 336;
                    PlayerService.gI().playerMove(mc, x, y);
                }
                lastTimeMove = System.currentTimeMillis();
            }
        }
    }
}
