package nro.models.Bot;

import com.mysql.jdbc.Messages;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import nro.models.map.ItemMap;
import nro.models.map.service.ItemMapService;
import nro.models.mob.Mob;
import nro.models.services.PlayerService;
import nro.models.services.*;
import nro.models.services.SkillService;
import nro.models.skill.Skill;
import nro.models.utils.Logger;
import nro.models.utils.Util;

public class BotPemQuai {

    private Mob mAttack;
    public long lastTimeChanM;
    public Bot bot;
    private boolean isPickingItem = false;
    private long lastSpecialSkillTime;
    private long lastChatTime = 0;
    private final long chatInterval = 3000;

    public BotPemQuai(Bot b) {
        this.bot = b;
    }

    private static final String[] chatMessages = {
        "|1|Con chó tài tỏi súc vật cali",
        "|2|bê ka tê địt mẹ tài tỏi tung lồn",
        "|3|arriety địt nhau với mẹ tài tỏi"
    };

    private void autoChat() {
        long now = System.currentTimeMillis();
        if (now - lastChatTime >= chatInterval) {
            String message;

            if (new Random().nextBoolean()) {
                message = chatMessages[new Random().nextInt(chatMessages.length)];
            } else if (this.bot != null) {
                message = this.bot.getChat();
            } else {
                return;
            }

            Service.gI().chat(this.bot, message);
            lastChatTime = now;
        }
    }

    public void update() {
        try {
            autoChat();
        } catch (Exception e) {
            Logger.logException(BotPemQuai.class, e);
        }

        Attack();
        chanGeMap();
        NhatItem();
    }

    public void GetMobAttack() {
        if (this.bot.zone.mobs.size() >= 1) {
            if (this.mAttack == null || this.mAttack.isDie()) {
                mAttack = this.bot.zone.mobs.get(new Random().nextInt(this.bot.zone.mobs.size()));
            }
        }
    }

    public void Attack() {
        this.GetMobAttack();
        if (this.mAttack == null || this.mAttack.isDie()) {
            return;
        }

        if (!bot.playerSkill.skills.isEmpty()) {
            Skill skill = bot.playerSkill.skills.get(new Random().nextInt(bot.playerSkill.skills.size()));
            bot.playerSkill.skillSelect = skill;
        }

        long now = System.currentTimeMillis();
        boolean isSpecialSkill = false;

        switch (bot.playerSkill.skillSelect.template.id) {
            case Skill.BIEN_KHI:
                isSpecialSkill = true;
                if (now - lastSpecialSkillTime >= 300_000) {
                    EffectSkillService.gI().sendEffectMonkey(bot);
                    EffectSkillService.gI().setIsMonkey(bot);
                    Service.gI().sendSpeedPlayer(bot, 5);
                    Service.gI().Send_Caitrang(bot);
                    Service.gI().sendSpeedPlayer(bot, -1);
                    PlayerService.gI().sendInfoHpMp(bot);
                    Service.gI().point(bot);
                    Service.gI().Send_Info_NV(bot);
                    Service.gI().sendInfoPlayerEatPea(bot);
                    SkillService.gI().sendPlayerPrepareSkill(bot, 300000);
                    lastSpecialSkillTime = now;
                }
                return;

            default:
                break;
        }

        if (isSpecialSkill && now - lastSpecialSkillTime < 300_000) {
            return;
        }

        boolean miss = false;
        int skillId = bot.playerSkill.skillSelect.template.id;
        if (skillId == Skill.KAMEJOKO || skillId == Skill.MASENKO || skillId == Skill.ANTOMIC) {
            if (mAttack != null) {
                int dame = Util.nextInt(10000, 15000);
            }
            SkillService.gI().useSkillAttack(bot, bot, mAttack);
            SkillService.gI().useSkillAlone(bot);
            return;
        }

        if (this.bot.UseLastTimeSkill()) {
            PlayerService.gI().playerMove(this.bot, this.mAttack.location.x, this.mAttack.location.y);
            SkillService.gI().useSkillAttack(bot, bot, mAttack);
            SkillService.gI().useSkillAlone(bot);
        }
    }

    public void chanGeMap() {
        if (this.lastTimeChanM < ((System.currentTimeMillis() - 150000) - new Random().nextInt(150000))) {
            this.bot.joinMap();
        }
    }

    private void NhatItem() {
        if (this.bot == null || this.bot.zone == null || this.bot.zone.items == null) {
            return;
        }
        if (isPickingItem) {
            return;
        }

        if (this.bot.charms != null && this.bot.charms.tdThuHut > System.currentTimeMillis()) {
            List<ItemMap> itemsToPick = new ArrayList<>();

            for (ItemMap item : new ArrayList<>(this.bot.zone.items)) {
                if (!item.isPickedUp
                        && Util.getDistance(this.bot.location.x, this.bot.location.y, item.x, item.y) < 500) {
                    itemsToPick.add(item);
                    if (itemsToPick.size() >= 3) {
                        break;
                    }
                }
            }

            if (itemsToPick.isEmpty()) {
                return;
            }

            isPickingItem = true;

            for (ItemMap item : itemsToPick) {
                try {
                    ItemMapService.gI().pickItem(bot, item.itemMapId, true);
                    item.isPickedUp = true;
                    this.bot.zone.items.remove(item);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    isPickingItem = false;
                }
            }, 500);
        }
    }
}
