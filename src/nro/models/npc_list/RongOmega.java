package nro.models.npc_list;
import nro.models.consts.ConstMap;
import nro.models.consts.ConstNpc;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.map.service.NpcService;
import nro.models.map.service.ChangeMapService;
import nro.models.utils.Logger;
import nro.models.utils.TimeUtil;

public class RongOmega extends Npc {

    public RongOmega(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {
            if (this.mapId == 24 || this.mapId == 25 || this.mapId == 26) {
                try {
                    if (TimeUtil.isBlackBallWarOpen()) {
                        if (TimeUtil.isBlackBallWarCanPick()) {
                            int index = 0;
                            for (int i = 0; i < 7; i++) {
                                if (player.rewardBlackBall.timeOutOfDateReward[i] > System.currentTimeMillis()) {
                                    index++;
                                }
                            }
                            if (index != 0) {
                                this.createOtherMenu(player, ConstNpc.MENU_OPEN_BDW, "Ngươi đang có phần thưởng ngọc sao đen, có muốn nhận không?",
                                        "Hướng\ndẫn\nthêm", "Tham gia", "Nhận\nthưởng", "Từ chối");
                                return;
                            }
                        }
                        this.createOtherMenu(player, ConstNpc.MENU_OPEN_BDW, "Đường đến với ngọc rồng sao đen đã mở, "
                                + "ngươi có muốn tham gia không?",
                                "Hướng\ndẫn\nthêm", "Tham gia", "Từ chối");
                    } else {
                        int index = 0;
                        for (int i = 0; i < 7; i++) {
                            if (player.rewardBlackBall.timeOutOfDateReward[i] > System.currentTimeMillis()) {
                                index++;
                            }
                        }
                        if (index != 0) {
                            this.createOtherMenu(player, ConstNpc.MENU_NOT_OPEN_BDW, "Ngươi đang có phần thưởng ngọc sao đen, có muốn nhận không?",
                                    "Hướng\ndẫn\nthêm", "Nhận\nthưởng", "Từ chối");
                            return;
                        }
                        this.createOtherMenu(player, ConstNpc.MENU_NOT_OPEN_BDW,
                                "Ta có thể giúp gì cho ngươi?", "Hướng\ndẫn\nthêm", "Từ chối");

                    }
                } catch (Exception ex) {
                    Logger.error("Lỗi mở menu rồng Omega");
                }
            }
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (canOpenNpc(player)) {
            switch (player.idMark.getIndexMenu()) {
                case ConstNpc.MENU_REWARD_BDW ->
                    player.rewardBlackBall.getRewardSelect((byte) select);
                case ConstNpc.MENU_OPEN_BDW -> {
                    switch (select) {
                        case 0 ->
                            NpcService.gI().createTutorial(player, tempId, this.avartar, ConstNpc.HUONG_DAN_BLACK_BALL_WAR);
                        case 1 -> {
                            player.idMark.setTypeChangeMap(ConstMap.CHANGE_BLACK_BALL);
                            ChangeMapService.gI().openChangeMapTab(player);
                        }
                        case 2 -> {
                            String[] optionRewards = new String[7];
                            int index = 0;
                            for (int i = 0; i < 7; i++) {
                                if (player.rewardBlackBall.timeOutOfDateReward[i] > System.currentTimeMillis()) {
                                    optionRewards[index] = "Nhận\nthưởng\n" + (i + 1) + " sao";
                                    index++;
                                }
                            }
                            if (index != 0) {
                                String[] options = new String[index];
                                System.arraycopy(optionRewards, 0, options, 0, index);
                                this.createOtherMenu(player, ConstNpc.MENU_REWARD_BDW, "Ngươi đang có phần thưởng ngọc sao đen, có muốn nhận không?",
                                        options);
                            }
                        }
                        default -> {
                        }
                    }
                }
                case ConstNpc.MENU_NOT_OPEN_BDW -> {
                    if (select == 0) {
                        NpcService.gI().createTutorial(player, tempId, this.avartar, ConstNpc.HUONG_DAN_BLACK_BALL_WAR);
                    } else if (select == 1) {
                        String[] optionRewards = new String[7];
                        int index = 0;
                        for (int i = 0; i < 7; i++) {
                            if (player.rewardBlackBall.timeOutOfDateReward[i] > System.currentTimeMillis()) {
                                optionRewards[index] = "Nhận\nthưởng\n" + (i + 1) + " sao";
                                index++;
                            }
                        }
                        if (index != 0) {
                            String[] options = new String[index];
                            System.arraycopy(optionRewards, 0, options, 0, index);
                            this.createOtherMenu(player, ConstNpc.MENU_REWARD_BDW, "Ngươi đang có phần thưởng ngọc sao đen, có muốn nhận không?",
                                    options);
                        }
                    }
                }
            }
            // 3 sao\n59 phút
        }
    }
}
