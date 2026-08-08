package nro.models.npc_list;

import nro.models.boss.BossID;
import nro.models.consts.ConstNpc;
import nro.models.services_dungeon.TrainingService;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.map.service.NpcService;
import nro.models.player.NPoint;
import nro.models.services.OpenPowerService;
import nro.models.services.Service;
import nro.models.utils.Util;

public class ToSuKaio extends Npc {

    public ToSuKaio(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {
            String message = String.format("Tập luyện với Tổ sư Kaio sẽ tăng %s sức mạnh mỗi phút, có thể tăng giảm tùy vào khả năng đánh quái của con",
                    Util.formatNumber(TrainingService.gI().getTnsmMoiPhut(player)));
            String autoTrainingOption = player.dangKyTapTuDong ? "Hủy đăng ký tập tự động" : "Đăng ký tập tự động";
            String autoTrainingMessage = player.dangKyTapTuDong ? "Hủy đăng\nký tập\ntự động" : "Đăng ký\ntập\ntự động";

            this.createOtherMenu(player, ConstNpc.BASE_MENU, message,
                    autoTrainingMessage, "Đồng ý\nluyện tập", "Nâng\nGiới hạn\nSức mạnh", "Không\nđồng ý");
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (!canOpenNpc(player)) {
            return;
        }

        int menuId = player.idMark.getIndexMenu();

        if (menuId == ConstNpc.BASE_MENU) {
            switch (select) {
                case 0 ->
                    handleAutoTrainingMenu(player);
                case 1 ->
                    TrainingService.gI().callBoss(player, BossID.TO_SU_KAIO, false);
                case 2 ->
                    showLimitPowerMenu(player);
            }
        } else if (menuId == ConstNpc.MENU_NANG_GIOI_HAN) {
            switch (select) {
                case 0 ->
                    showLimitPowerMyself(player);
                case 1 ->
                    handleLimitPowerPet(player, 0);
                default ->
                    this.npcChat(player, "Khi nào con cần thì quay lại gặp ta!");
            }
        } else if (menuId == ConstNpc.OPEN_POWER_MYSEFT) {
            handleLimitPowerMyselfOptions(player, select);
        } else if (menuId == ConstNpc.OPEN_POWER_PET) {
            handleLimitPowerPet(player, select);
        } else if (menuId == 2001) {
            handleAutoTrainingRegistration(player, select);
        }
    }

    private void showLimitPowerMenu(Player player) {
        this.createOtherMenu(player, ConstNpc.MENU_NANG_GIOI_HAN,
                "Con muốn nâng giới hạn sức mạnh cho bản thân hay đệ tử?",
                "Bản thân", "Đệ tử", "Từ chối");
    }

    private void showLimitPowerMyself(Player player) {
        if (player.nPoint.limitPower < NPoint.MAX_LIMIT) {
            if (player.nPoint.limitPower >= 5 && player.nPoint.limitPower < 9) {
                this.createOtherMenu(player, ConstNpc.OPEN_POWER_MYSEFT,
                        "Ta sẽ truyền năng lượng giúp con mở giới hạn sức mạnh của bản thân lên "
                        + Util.numberToMoney(player.nPoint.getPowerNextLimit()),
                        "Nâng\ngiới hạn\nsức mạnh",
                        "Nâng ngay\n" + Util.numberToMoney(OpenPowerService.COST_SPEED_OPEN_LIMIT_POWER) + " vàng",
                        "Đóng");
            } else {
                this.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                        "Yêu cầu đạt 50 tỷ sức mạnh", "Đóng");
            }
        } else {
            this.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                    "Sức mạnh của con đã đạt tới giới hạn",
                    "Đóng");
        }
    }

    private void handleLimitPowerMyselfOptions(Player player, int select) {
        if (player.nPoint.limitPower < 5 || player.nPoint.limitPower >= 9) {
            Service.gI().sendThongBao(player, "Sức mạnh của ngươi không đủ 50 tỷ!");
            return;
        }

        switch (select) {
            case 0 ->
                OpenPowerService.gI().openPowerBasic(player);
            case 1 -> {
                if (player.inventory.gold >= OpenPowerService.COST_SPEED_OPEN_LIMIT_POWER) {
                    if (OpenPowerService.gI().openPowerSpeed(player)) {
                        player.inventory.gold -= OpenPowerService.COST_SPEED_OPEN_LIMIT_POWER;
                        Service.gI().sendMoney(player);
                    }
                } else {
                    Service.gI().sendThongBao(player, "Bạn không đủ vàng để mở, còn thiếu "
                            + Util.numberToMoney(OpenPowerService.COST_SPEED_OPEN_LIMIT_POWER - player.inventory.gold) + " vàng");
                }
            }
        }
    }

    private void handleLimitPowerPet(Player player, int select) {
        if (select != 0) {
            return;
        }

        if (player.pet == null) {
            Service.gI().sendThongBao(player, "Không thể thực hiện");
            return;
        }

        if (player.pet.nPoint.limitPower < 5 || player.pet.nPoint.limitPower >= 9) {
            this.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                    "Đệ tử của bạn cần đạt 50 tỷ sức mạnh!", "Đóng");
            return;
        }

        if (player.inventory.gold >= OpenPowerService.COST_SPEED_OPEN_LIMIT_POWER) {
            if (OpenPowerService.gI().openPowerSpeed(player.pet)) {
                player.inventory.gold -= OpenPowerService.COST_SPEED_OPEN_LIMIT_POWER;
                Service.gI().sendMoney(player);
            }
        } else {
            Service.gI().sendThongBao(player, "Bạn không đủ vàng để mở, còn thiếu "
                    + Util.numberToMoney(OpenPowerService.COST_SPEED_OPEN_LIMIT_POWER - player.inventory.gold) + " vàng");
        }
    }

    private void handleAutoTrainingMenu(Player player) {
        if (player.dangKyTapTuDong) {
            player.dangKyTapTuDong = false;
            NpcService.gI().createTutorial(player, tempId, avartar, "Con đã hủy thành công đăng ký tập tự động\nTừ giờ con muốn tập Offline hãy tự đến đây trước");
        } else {
            showAutoTrainingRegistrationMenu(player);
        }
    }

    private void showAutoTrainingRegistrationMenu(Player player) {
        String message = String.format("Đăng ký để mỗi khi Offline quá 30 phút, con sẽ được tự động luyện tập với tốc độ %s sức mạnh mỗi phút",
                TrainingService.gI().getTnsmMoiPhut(player));
        this.createOtherMenu(player, 2001, message, "Hướng\ndẫn\nthêm", "Đồng ý\n1 vàng\nmỗi lần", "Không\nđồng ý");
    }

    private void handleAutoTrainingRegistration(Player player, int select) {
        switch (select) {
            case 0 ->
                NpcService.gI().createTutorial(player, tempId, avartar, ConstNpc.TAP_TU_DONG);
            case 1 -> {
                player.mapIdDangTapTuDong = mapId;
                player.dangKyTapTuDong = true;
                NpcService.gI().createTutorial(player, tempId, avartar, "Từ giờ, quá 30 phút Offline con sẽ được tự động luyện tập");
            }
        }
    }
}
