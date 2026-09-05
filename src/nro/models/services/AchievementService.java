package nro.models.services;

import nro.models.consts.ConstAchievement;
import nro.models.mob.Mob;
import nro.models.player_system.Template.AchievementTemplate;
import nro.models.network.Message;
import nro.models.player.Achievement;
import nro.models.player.Player;
import nro.models.server.Manager;
import nro.models.skill.Skill;
import nro.models.utils.Logger;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 *
 */
public class AchievementService {

    private static AchievementService instance;

    public static AchievementService gI() {
        if (instance == null) {
            instance = new AchievementService();
        }
        return instance;
    }

    public void openAchievementUI(Player player) {
        Message msg = null;
        try {
            msg = new Message(-76);
            msg.writer().writeByte(0);
            msg.writer().writeByte(Manager.ACHIEVEMENT_TEMPLATE.size());
            for (int i = 0; i < Manager.ACHIEVEMENT_TEMPLATE.size(); i++) {
                AchievementTemplate at = Manager.ACHIEVEMENT_TEMPLATE.get(i);
                msg.writer().writeUTF(at.info1); // info 1
                msg.writer().writeUTF(regex(player, at.info2) + " (" + numberToString(player.achievement.getCompleted(i)) + "/" + numberToString(at.maxCount) + ")"); // info 2
                msg.writer().writeShort(at.money); // money
                msg.writer().writeBoolean(player.achievement.isFinish(i, at.maxCount));// isFinish
                msg.writer().writeBoolean(player.achievement.isRecieve(i)); // isRecieve
            }
            player.sendMessage(msg);
        } catch (Exception e) {
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void confirmAchievement(Player player, byte select) {
        confirmAchievement(player, (int) select);
    }

    public void confirmAchievement(Player player, int select) {
        if (player == null || player.achievement == null) {
            return;
        }
        Achievement.ClaimResult result = player.achievement.claimReward(select, true);
        if (result.isSuccess()) {
            if (player.inventory != null) {
                InventoryService.gI().sendItemBags(player);
                Service.gI().sendMoney(player);
            }
            Service.gI().sendThongBao(player, "Bạn vừa nhận được " + result.getRewardGem() + " ngọc.");
            Message msg = null;
            try {
                msg = new Message(-76);
                msg.writer().writeByte(1);
                msg.writer().writeByte(select);
                player.sendMessage(msg);
            } catch (Exception e) {
                Logger.error("[AchievementService] Failed to send success packet for player=" + player.id + ", achievement=" + select + ", error=" + e.getClass().getName() + "\n");
            } finally {
                if (msg != null) {
                    msg.cleanup();
                }
            }
        } else {
            switch (result.getStatus()) {
                case BAG_FULL ->
                    Service.gI().sendThongBao(player, "Cần tối thiểu 1 ô trống hành trang để nhận thưởng");
                case ALREADY_CLAIMED ->
                    Service.gI().sendThongBao(player, "Bạn đã nhận phần thưởng này rồi");
                case NOT_COMPLETED ->
                    Service.gI().sendThongBao(player, "Bạn chưa hoàn thành thành tựu này");
                case GEM_LIMIT ->
                    Service.gI().sendThongBao(player, "Hành trang ngọc đã đạt giới hạn, vui lòng dùng bớt ngọc");
                case INVALID_BALANCE ->
                    Service.gI().sendThongBao(player, "Số dư ngọc không hợp lệ, vui lòng liên hệ quản trị viên");
                case PLAYER_UNAVAILABLE ->
                    Service.gI().sendThongBao(player, "Không thể nhận thưởng lúc này");
                case INVALID_INDEX, PLAYER_NULL -> {
                }
            }
        }
    }

    public String numberToString(long num) {
        return num <= 10000 ? num + "" : Util.numberToMoney(num);
    }

    public String regex(Player player, String text) {
        int gen = player.gender;
        return text.replaceAll("%1", gen == 0 ? "Siêu nhân" : gen == 1 ? "Siêu Namếc" : "Siêu Xayda").replaceAll("%2", gen == 0 ? "Bunma" : gen == 1 ? "Dende" : "Appule");
    }

    public void checkDoneTask(Player player, int aId) {
        if (player.isPl() && player.achievement != null) {
            switch (aId) {
                case ConstAchievement.LAN_DAU_NAP_NGOC ->
                    player.achievement.doneNotAdd(aId, player.getSession().tongnap);
                default ->
                    player.achievement.done(aId, 1);
            }
        }
    }

    public void checkDoneTaskKillMob(Player player, Mob mob) {
        try {
            if (mob.lvMob > 0) {
                checkDoneTask(player, ConstAchievement.DANH_BAI_SIEU_QUAI);
            }
            if (mob.type == 4) {
                checkDoneTask(player, ConstAchievement.THO_SAN_THIEN_XA);
            }
            if (mob.tempId == 0) {
                checkDoneTask(player, ConstAchievement.TAP_LUYEN_BAI_BAN);
            }
        } catch (Exception e) {
        }
    }

    public void checkDoneTaskUseSkill(Player player) {
        if (player.isPl()) {
            switch (player.playerSkill.skillSelect.template.id) {
                case Skill.KAMEJOKO, Skill.MASENKO, Skill.ANTOMIC -> {
                    checkDoneTask(player, ConstAchievement.NOI_CONG_CAO_CUONG);
                }
                case Skill.DRAGON, Skill.DEMON, Skill.GALICK, Skill.LIEN_HOAN, Skill.KAIOKEN, Skill.DICH_CHUYEN_TUC_THOI -> {
                }
                default ->
                    checkDoneTask(player, ConstAchievement.KY_NANG_THANH_THAO);
            }
        }
    }

    public void checkDoneTaskFly(Player player, int length) {
        if (player.isPl() && player.achievement != null) {
            length = Math.abs(length / 10);
            if (length < 10) {
                player.achievement.done(ConstAchievement.KHINH_CONG_THANH_THAO, length);
            }
        }
    }

}
