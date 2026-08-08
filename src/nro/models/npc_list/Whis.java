package nro.models.npc_list;

import nro.models.boss.BossID;
import nro.models.combine.CombineService;
import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import java.io.IOException;
import nro.models.map.service.ChangeMapService;
import nro.models.network.Message;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.server.Manager;
import nro.models.services.Service;
import nro.models.services.SkillService;
import nro.models.services_dungeon.TrainingService;
import nro.models.shop.ShopService;
import nro.models.skill.Skill;
import nro.models.utils.SkillUtil;
import nro.models.utils.Util;

public class Whis extends Npc {

    private static final int COST_HD = 50_000_000;

    public Whis(int mapId, int status, int cx, int cy, int tempId, int avatar) {
        super(mapId, status, cx, cy, tempId, avatar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (!canOpenNpc(player)) {
            return;
        }

        switch (this.mapId) {
            case 154 ->
                createOtherMenu(player, ConstNpc.BASE_MENU,
                        "Thử đánh với ta xem nào.\nNgươi còn 1 lượt nữa cơ mà.",
                        "Nói chuyện", "Học\ntuyệt kỹ", "Top 100", "[LV:" + (player.traning.getTop() + 1) + "]");
            case 164 ->
                createOtherMenu(player, ConstNpc.BASE_MENU,
                        "Ta có thể giúp gì cho ngươi?", "Quay về", "Từ chối");
            case 48 ->
                createOtherMenu(player, ConstNpc.BASE_MENU, "Coming Soon");
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (!canOpenNpc(player)) {
            return;
        }

        if (player.idMark.isBaseMenu()) {
            handleBaseMenu(player, select);
        } else {
            switch (player.idMark.getIndexMenu()) {
                case 5 ->
                    handleCheTaoTrangBiThienSu(player, select);
                case CombineService.CHE_TAO_TRANG_BI_THIEN_SU -> {
                    if (select == 0) {
                        CombineService.gI().startCombine(player);
                    }
                }
                case 6 ->
                    handleHocTuyetKy(player, select);
            }
        }
    }

    private void handleBaseMenu(Player player, int select) {
        Item biKiepTuyetKy = InventoryService.gI().findItem(player.inventory.itemsBag, 1229);

        switch (select) {
            case 0 -> {
                if (this.mapId == 154) {
                    createOtherMenu(player, 5,
                            "Ta sẽ giúp ngươi chế tạo trang bị thiên sứ",
                            "Shop thiên sứ", "Chế tạo", "Từ chối");
                } else if (this.mapId == 164) {
                    ChangeMapService.gI().changeMapInYard(player, 154, -1, 758);
                }
            }
            case 2 ->
                Service.gI().showListTop(player, Manager.Topwhis);
            case 1 ->
                showSkillLearningMenu(player, biKiepTuyetKy);
            case 3 ->
                TrainingService.gI().callBoss(player, BossID.WHIS, false);
        }
    }

    private void handleCheTaoTrangBiThienSu(Player player, int select) {
        switch (select) {
            case 0 ->
                ShopService.gI().opendShop(player, "THIEN_SU", false);
            case 1 -> {
                if (!player.setClothes.checkSetDes()) {
                    createOtherMenu(player, ConstNpc.IGNORE_MENU,
                            "Ngươi hãy trang bị đủ 5 món trang bị Hủy Diệt rồi ta nói chuyện tiếp.", "OK");
                } else {
                    CombineService.gI().openTabCombine(player, CombineService.CHE_TAO_TRANG_BI_THIEN_SU);
                }
            }
        }
    }

    private void showSkillLearningMenu(Player player, Item biKiep) {
        int skillId = switch (player.gender) {
            case 0 ->
                Skill.SUPER_KAME;
            case 1 ->
                Skill.MA_PHONG_BA;
            default ->
                Skill.LIEN_HOAN_CHUONG;
        };

        Skill currentSkill = SkillUtil.getSkillbyId(player, skillId);
        boolean canLearn = (currentSkill == null || currentSkill.point == 0);

        String skillName = switch (player.gender) {
            case 0 ->
                "Super kamejoko";
            case 1 ->
                "Ma phong ba";
            default ->
                "Ca đíc liên hoàn chưởng";
        };

        int requiredBk = canLearn ? 9999 : 999;
        String message = "|1|Ta sẽ dạy ngươi tuyệt kỹ " + skillName
                + (canLearn ? " 1" : " " + (currentSkill.point + 1));

        if (biKiep != null) {
            message += "\n|7|Bí kiếp tuyệt kỹ: " + biKiep.quantity + "/" + requiredBk;
        } else {
            message += "\n|7|Bí kiếp tuyệt kỹ: 0/" + requiredBk;
        }

        message += "\n|2|Giá vàng: 10.000.000\n|2|Giá ngọc: 99";
        createOtherMenu(player, 6, message, "Đồng ý", "Từ chối");

    }

    private void handleHocTuyetKy(Player player, int select) {
        if (select != 0) {
            return;
        }

        Item sach = InventoryService.gI().findItemBag(player, 1229);
        if (sach == null) {
            return;
        }

        if (player.inventory.gold < 10_000_000 || player.inventory.gem < 99 || player.nPoint.power < 60_000_000_000L) {
            Service.gI().sendThongBao(player, "Bạn không đủ điều kiện học tuyệt kỹ!");
            return;
        }

        int skillId = player.gender == 0 ? Skill.SUPER_KAME : (player.gender == 1 ? Skill.MA_PHONG_BA : Skill.LIEN_HOAN_CHUONG);
        int iconSkill = player.gender == 0 ? 11162 : (player.gender == 1 ? 11194 : 11193);
        Skill currentSkill = SkillUtil.getSkillbyId(player, skillId);

        boolean isNewSkill = (currentSkill == null || currentSkill.point == 0);

        if (isNewSkill) {
            if (sach.quantity >= 9999) {
                learnNewSkill(player, sach, skillId, iconSkill);
            } else {
                int missing = 9999 - sach.quantity;
                Service.gI().sendThongBao(player, "Ngươi còn thiếu " + missing + " bí kíp nữa.\nHãy tìm đủ rồi đến gặp ta.");
            }
        } else {
            if (sach.quantity >= 999) {
                if (currentSkill.currLevel < 1000) {
                    npcChat(player, "Ngươi chưa luyện skill đến mức thành thạo. Luyện thêm đi.");
                } else if (currentSkill.point >= 9) {
                    npcChat(player, "Skill của ngươi đã đạt cấp tối đa.");
                } else {
                    upgradeSkill(player, sach, currentSkill);
                }
            } else {
                int missing = 999 - sach.quantity;
                Service.gI().sendThongBao(player, "Ngươi còn thiếu " + missing + " bí kíp nữa.\nHãy tìm đủ rồi đến gặp ta.");
            }
        }
    }

    private void learnNewSkill(Player player, Item sach, int skillId, int iconSkill) {
        try {
            boolean success = Util.isTrue(15, 15);
            String message = success ? "Học skill thành công!" : "Tư chất kém!";
            String npcMessage = success ? "Chúc mừng con nhé!" : "Ngu dốt!";
            int usedBk = success ? 9999 : 99;
            if (success) {
                SkillService.gI().learSkillSpecial(player, (byte) skillId);
            } else {
                iconSkill = 15313;
            }

            sendLearnSkillEffect(player, iconSkill, usedBk);
            npcChat(player, npcMessage);
            Service.gI().sendThongBao(player, message);

            InventoryService.gI().subQuantityItemsBag(player, sach, usedBk);
            player.inventory.gold -= 10_000_000;
            player.inventory.gem -= 99;
            InventoryService.gI().sendItemBags(player);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void upgradeSkill(Player player, Item sach, Skill currentSkill) {
        try {
            boolean success = Util.isTrue(1, 30);
            String message = success ? "Nâng skill thành công!" : "Tư chất kém!";
            String npcMessage = success ? "Chúc mừng con nhé!" : "Ngu dốt!";
            int usedBk = success ? 999 : 99;
            if (success) {
                currentSkill.point++;
            }

            sendLearnSkillEffect(player, 15313, usedBk);
            npcChat(player, npcMessage);
            Service.gI().sendThongBao(player, message);

            InventoryService.gI().subQuantityItemsBag(player, sach, usedBk);
            player.inventory.gold -= 10_000_000;
            player.inventory.gem -= 99;
            InventoryService.gI().sendItemBags(player);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendLearnSkillEffect(Player player, int iconSkill, int usedBk) throws IOException {
        Message msg = new Message(-81);
        msg.writer().writeByte(0);
        msg.writer().writeUTF("Skill 9");
        msg.writer().writeUTF("null");
        msg.writer().writeShort(tempId);
        player.sendMessage(msg);
        msg.cleanup();

        msg = new Message(-81);
        msg.writer().writeByte(1);
        msg.writer().writeByte(1);
        msg.writer().writeByte(InventoryService.gI().getIndexItemBag(player, InventoryService.gI().findItemBag(player, 1229)));
        player.sendMessage(msg);
        msg.cleanup();

        msg = new Message(-81);
        msg.writer().writeByte(usedBk == 99 ? 8 : 7);
        msg.writer().writeShort(iconSkill);
        player.sendMessage(msg);
        msg.cleanup();
    }
}
