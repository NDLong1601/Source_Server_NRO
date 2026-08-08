package nro.models.combine;

import nro.models.consts.ConstFont;
import nro.models.consts.ConstNpc;
import nro.models.player_system.Template;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.Service;
import nro.models.services.SkillService;
import nro.models.skill.Skill;
import nro.models.utils.SkillUtil;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 */

public class HocTuyetKy {

    public static void showInfoCombine(Player player) {
        Item biKipTuyetKy = InventoryService.gI().findItem(player.inventory.itemsBag, 1229);
        int quantityBiKipTuyetKy = (biKipTuyetKy != null) ? biKipTuyetKy.quantity : 0;
        int gem = player.inventory.getGem();
        long gold = player.inventory.gold;
        int skillId = getSkillIdByGender(player.gender);
        Skill curSkill = SkillUtil.getSkillbyId(player, skillId);
        int nextPoint = (curSkill != null && curSkill.point > 0) ? curSkill.point + 1 : 1;

        if (nextPoint > 7) {
            Service.gI().sendServerMessage(player, "Kỹ năng đã đạt tối đa!");
            return;
        }

        Template.SkillTemplate skillTemplate = SkillUtil.findSkillTemplate(skillId);
        StringBuilder text = new StringBuilder();
        text.append(ConstFont.BOLD_GREEN)
            .append("Qua sẽ dạy ngươi tuyệt kỹ ")
            .append(skillTemplate.name)
            .append(" ")
            .append(nextPoint)
            .append("\n");

        int requiredBiKip = (nextPoint == 1) ? 9999 : 999;
        int requiredGem = (nextPoint == 1) ? 99 : 0;
        int requiredGold = 10_000_000;

        appendRequirement(text, "Bí kíp tuyệt kỹ", quantityBiKipTuyetKy, requiredBiKip);
        appendRequirement(text, "Giá vàng", gold, requiredGold);
        if (requiredGem > 0) {
            appendRequirement(text, "Giá ngọc", gem, requiredGem);
        }

        if (quantityBiKipTuyetKy < requiredBiKip || gold < requiredGold || gem < requiredGem) {
            CombineService.gI().whis.createOtherMenu(player, ConstNpc.IGNORE_MENU, text.toString(), "Từ chối");
            return;
        }

        CombineService.gI().whis.createOtherMenu(player, ConstNpc.HOC_TUYET_KY, text.toString(), "Đồng ý", "Từ chối");
    }

    public static void hocTuyetKy(Player player) {
        Item biKipTuyetKy = InventoryService.gI().findItem(player.inventory.itemsBag, 1229);
        int quantityBiKipTuyetKy = (biKipTuyetKy != null) ? biKipTuyetKy.quantity : 0;
        int gem = player.inventory.getGem();
        long gold = player.inventory.gold;
        int skillId = getSkillIdByGender(player.gender);
        Skill curSkill = SkillUtil.getSkillbyId(player, skillId);
        int nextPoint = (curSkill != null && curSkill.point > 0) ? curSkill.point + 1 : 1;

        if (nextPoint > 7) {
            return;
        }

        int requiredBiKip = (nextPoint == 1) ? 9999 : 999;
        int requiredGem = (nextPoint == 1) ? 99 : 0;
        int requiredGold = 10_000_000;

        if (quantityBiKipTuyetKy < requiredBiKip || gold < requiredGold || gem < requiredGem) {
            return;
        }

        Template.SkillTemplate skillTemplate = SkillUtil.findSkillTemplate(skillId);
        Skill nextSkill = SkillUtil.createSkill(skillTemplate.id, nextPoint);
        SkillUtil.setSkill(player, nextSkill);

        player.inventory.subGem(requiredGem);
        player.inventory.gold -= requiredGold;
        InventoryService.gI().subQuantityItemsBag(player, biKipTuyetKy, requiredBiKip);

        CombineService.gI().whis.npcChat(player, "Bư cô lô, ba cô la, bư ra bư zô...");
        CombineService.gI().sendEffSuccessVip(player, skillTemplate.iconId);
        InventoryService.gI().sendItemBags(player);
    }

    private static int getSkillIdByGender(int gender) {
        switch (gender) {
            case 0:
                return Skill.SUPER_KAME;
            case 1:
                return Skill.MA_PHONG_BA;
            default:
                return Skill.LIEN_HOAN_CHUONG;
        }
    }

    private static void appendRequirement(StringBuilder text, String label, long current, long required) {
        text.append((current < required) ? ConstFont.BOLD_RED : ConstFont.BOLD_BLUE)
            .append(label)
            .append(": ")
            .append(current)
            .append("/")
            .append(required)
            .append("\n");
    }
}