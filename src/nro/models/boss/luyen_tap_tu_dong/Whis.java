package nro.models.boss.luyen_tap_tu_dong;

import nro.models.boss.BossID;
import nro.models.consts.BossStatus;
import nro.models.boss.BossesData;
import static nro.models.consts.BossType.PHOBAN;
import nro.models.database.PlayerDAO;
import nro.models.database.TraningDAO;
import nro.models.player.Player;
import nro.models.services.Service;
import nro.models.map.service.ChangeMapService;
import nro.models.server.Manager;
import static nro.models.server.Manager.isTopWhisChanged;
import nro.models.utils.Util;

public class Whis extends TrainingBoss {

    public Whis(Player player) throws Exception {
        super(PHOBAN, BossID.WHIS, BossesData.WHIS);
        playerAtt = player;
        Service.gI().setPos0(player, 488, 360);
    }

    private long lastJoinMapTime;
    private boolean isTele;
    private boolean isChatS;
    private long level;

    @Override
    public void joinMap() {
        if (playerAtt.zone != null) {
            this.zone = playerAtt.zone;
            this.level = playerAtt.traning.getTop() + 1;
            this.nPoint.hpMax *= level;
            this.nPoint.hp = this.nPoint.hpMax;
            this.nPoint.dame *= level;
            this.name = "Whis [LV:" + level + "]";
            ChangeMapService.gI().changeMap(this, this.zone, 725, 312);
            this.chat("Ta sẽ dạy ngươi vài chiêu");
            this.changeStatus(BossStatus.AFK);
            lastTimeAFK = System.currentTimeMillis();
            lastJoinMapTime = System.currentTimeMillis();
        }
    }

    @Override
    public boolean chatS() {
        if (Util.canDoWithTime(lastTimeChatS, timeChatS)) {
            if (this.doneChatS) {
                return true;
            }
            String textChat = this.data[this.currentLevel].getTextS()[playerAtt.isThachDau ? 1 : 0];
            int prefix = Integer.parseInt(textChat.substring(1, textChat.lastIndexOf("|")));
            textChat = textChat.substring(textChat.lastIndexOf("|") + 1);
            if (!this.chat(prefix, textChat)) {
                return false;
            }
            this.lastTimeChatS = System.currentTimeMillis();
            this.timeChatS = 2000;
            doneChatS = true;
        }
        return false;
    }

    @Override
    public synchronized int injured(Player plAtt, long damage, boolean piercing, boolean isMobAttack) {
        if (!piercing && Util.isTrue(400, 1000)) {
            this.chat("Xí hụt");
            return 0;
        }
        damage /= level;
        this.nPoint.subHP(damage);
        if (this.nPoint.hp > 0 && this.nPoint.hp < this.nPoint.hpMax / 3) {
            if (Util.canDoWithTime(lastTimeChat, 2000)) {
                String[] text = {"AAAAAAAAA", "ai da"};
                this.chat(text[Util.nextInt(text.length)]);
            }
        }
        if (damage >= this.nPoint.hp) {
            this.setDie(plAtt);
            die(plAtt);
        }
        return (int) damage;
    }

    @Override
    public void die(Player plKill) {
        this.changeStatus(BossStatus.AFK);
        this.chatE();
        this.lastTimeAFK = System.currentTimeMillis();

        if (playerAtt != null && plKill != null) {
            Service.gI().sendPlayerVS(playerAtt, plKill, (byte) 0);
        } else if (playerAtt != null) {
            Service.gI().sendPlayerVS(playerAtt, playerAtt, (byte) 0);
        }

        if (plKill != null) {
            plKill.thachdauwhis++;
            if (!Manager.isTopWhisChanged) {
                Manager.isTopWhisChanged = true;
            }
            Service.gI().sendMoney(plKill);
            PlayerDAO.updatePlayer(plKill);

            try {
                playerAtt.traning.setTop((int) level);
                playerAtt.traning.setTime((int) (System.currentTimeMillis() - lastJoinMapTime));
                playerAtt.traning.setLastTime(System.currentTimeMillis());
                TraningDAO.updatePlayer(playerAtt);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean chatE() {
        if (Util.canDoWithTime(lastTimeChatE, timeChatE)) {
            if (this.indexChatE == 1) {
                return true;
            }
            String[] text = new String[]{"OK ta chịu thua", "Ta rất tự hào về con", "Tại hôm nay ta...ta hơi bị đau bụng", "Thua thì thua"};
            String t = text[Util.nextInt(text.length)];
            this.chat(t);
            this.lastTimeChatE = System.currentTimeMillis();
            this.timeChatE = t.length() * 100;
            if (this.timeChatE > 2000) {
                this.timeChatE = 2000;
            }
            this.indexChatE = 1;
        }
        return false;
    }

    @Override
    public void afk() {
        if (Util.canDoWithTime(lastJoinMapTime, 50) && !isTele) {
            isTele = true;
            Service.gI().setPos(this, 341, 320);
        } else if (Util.canDoWithTime(lastJoinMapTime, 500) && !isChatS) {
            isChatS = true;
            this.changeStatus(BossStatus.CHAT_S);
        } else if (Util.canDoWithTime(lastTimeAFK, 1000) && isTele && isChatS) {
            this.changeStatus(BossStatus.LEAVE_MAP);
        }
    }
}
