package nro.models.npc;

import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.map.service.NpcManager;
import nro.models.player.Player;
import nro.models.services.Service;
import nro.models.utils.Util;
import nro.models.network.Message;
import nro.models.services.ItemService;
import nro.models.services.PlayerService;
import nro.models.services.InventoryService;
import nro.models.utils.Logger;

public class MagicTree {

    public static final byte MAX_LEVEL = 10;

    public static final short[] PEA_TEMP = {13, 60, 61, 62, 63, 64, 65, 352, 523, 595};
    public static final int[] PEA_PARAM = {100, 500, 2, 4, 8, 16, 32, 64, 128, 256};

    private static final int[][][] POS_PEAS = {
        {{19, 22}, {-1, 16}, {3, 10}, {19, 8}, {9, 0}},
        {{-1, 27}, {22, 35}, {15, 24}, {0, 17}, {-1, 7}, {26, 5}, {5, 0}},
        {{25, 41}, {-1, 40}, {25, 34}, {3, 32}, {25, 23}, {10, 19}, {2, 12}, {17, 10}, {4, 5}},
        {{3, 44}, {21, 49}, {25, 39}, {4, 30}, {29, 25}, {0, 18}, {21, 15}, {14, 39}, {18, 25}, {4, 7}, {15, 0}},
        {{21, 58}, {0, 56}, {18, 48}, {10, 0}, {25, 38}, {0, 26}, {14, 28}, {25, 16}, {1, 14}, {22, 7}, {10, 14}, {28, 23}, {15, 16}},
        {{25, 63}, {0, 66}, {21, 52}, {3, 55}, {14, 60}, {3, 45}, {22, 43}, {10, 35}, {22, 28}, {3, 28}, {18, 17}, {3, 14}, {17, 6}, {11, 22}, {6, 1}},
        {{32, 86}, {5, 77}, {25, 77}, {8, 89}, {29, 68}, {4, 63}, {18, 61}, {33, 53}, {8, 48}, {26, 39}, {11, 36}, {33, 23}, {18, 25}, {4, 20}, {26, 12}, {12, 7}, {19, 0}},
        {{32, 86}, {5, 77}, {25, 77}, {8, 89}, {29, 68}, {4, 63}, {18, 61}, {33, 53}, {8, 48}, {26, 39}, {11, 36}, {33, 23}, {18, 25}, {4, 20}, {26, 12}, {12, 7}, {19, 0}, {19, 0}, {19, 0}},
        {{32, 86}, {5, 77}, {25, 77}, {8, 89}, {29, 68}, {4, 63}, {18, 61}, {33, 53}, {8, 48}, {26, 39}, {11, 36}, {33, 23}, {18, 25}, {4, 20}, {26, 12}, {12, 7}, {19, 0}, {19, 0}, {19, 0}, {19, 0}, {19, 0}},
        {{32, 86}, {5, 77}, {25, 77}, {8, 89}, {29, 68}, {4, 63}, {18, 61}, {33, 53}, {8, 48}, {26, 39}, {11, 36}, {33, 23}, {18, 25}, {4, 20}, {26, 12}, {12, 7}, {19, 0}, {19, 0}, {19, 0}, {19, 0}, {19, 0}, {19, 0}, {19, 0}}};

    //days - hours - mins - gold
    private static final short[][] PEA_UPGRADE = {
        {0, 0, 10, 5}, {0, 1, 40, 10}, {0, 16, 40, 100}, {6, 22, 0, 1},
        {13, 21, 0, 10}, {27, 18, 0, 20}, {55, 13, 0, 50}, {69, 10, 0, 100},
        {104, 4, 0, 300}, {0, 0, 0, 0}
    };

    //icon magic tree [gender][level]
    private static final short[][] ID_MAGIC_TREE = {
        {84, 85, 86, 87, 88, 89, 90, 90, 90, 90},
        {371, 372, 373, 374, 375, 376, 377, 377, 377, 377},
        {378, 379, 380, 381, 382, 383, 384, 384, 384, 384}};
    private static final short[][] POS_MAGIC_TREE = {{348, 336}, {372, 336}, {348, 336}};

    private static final int[] UPGRADE_GEM = {20, 50, 120, 300, 800, 1500, 3000, 6000, 7500, 10000};
    private static final int[] HARVEST_GEM = {1, 2, 5, 7, 9, 12, 15, 20, 25, 30};

    private boolean loadedMagicTreeToPlayer;
    private Player player;

    public byte level;
    public int currPeas;
    public boolean isUpgrade;
    public long lastTimeHarvest;
    public long lastTimeUpgrade;

    public MagicTree(Player player, byte level, byte currPeas, long lastTimeHarvest, boolean isUpgrade, long lastTimeUpgrade) {
        this.player = player;
        this.level = level;
        this.currPeas = currPeas;
        if (this.currPeas > this.getMaxPea()) {
            this.currPeas = this.getMaxPea();
        }
        this.isUpgrade = isUpgrade;
        this.lastTimeHarvest = lastTimeHarvest;
        this.lastTimeUpgrade = lastTimeUpgrade;
    }

    public void update() {
        if (!isUpgrade) {
            if (this.currPeas < this.getMaxPea()) {
                int timeThrow = (int) ((System.currentTimeMillis() - lastTimeHarvest) / 1000);
                int numPeaRelease = timeThrow / getSecondPerPea();
                if (numPeaRelease > 0) {
                    this.currPeas += numPeaRelease;
                    if (this.currPeas >= this.getMaxPea()) {
                        this.currPeas = this.getMaxPea();
                        this.lastTimeHarvest = System.currentTimeMillis();
                    } else {
                        this.lastTimeHarvest += (numPeaRelease * getSecondPerPea()) * 1000;
                    }
                }
            }
        } else {
            if (Util.canDoWithTime(lastTimeUpgrade, getTimeUpgrade())) {
                if (this.level < MAX_LEVEL) {
                    this.level++;
                }
                this.isUpgrade = false;
            }
        }
    }

    public void loadMagicTree() {
        Message msg;
        try {
            msg = new Message(-34);
            msg.writer().writeByte(0);

            msg.writer().writeShort(ID_MAGIC_TREE[player.gender][level - 1]);

            msg.writer().writeUTF("Đậu thần cấp " + level);
            msg.writer().writeShort(POS_MAGIC_TREE[player.gender][0]);
            msg.writer().writeShort(POS_MAGIC_TREE[player.gender][1]);
            msg.writer().writeByte(level);
            msg.writer().writeShort(this.currPeas);
            msg.writer().writeShort(getMaxPea());
            msg.writer().writeUTF("Đang kết hạt\nCây lớn sinh nhiều hạt hơn");
            msg.writer().writeInt(this.isUpgrade ? getSecondUpgrade() : getSecondPea()); //seconds
            msg.writer().writeByte(POS_PEAS[this.level - 1].length); //pos pea
            for (int i = 0; i < POS_PEAS[this.level - 1].length; i++) {
                msg.writer().writeByte(POS_PEAS[this.level - 1][i][0]);
                msg.writer().writeByte(POS_PEAS[this.level - 1][i][1]);
            }
            msg.writer().writeBoolean(this.isUpgrade);
            player.sendMessage(msg);
            msg.cleanup();
            if (!loadedMagicTreeToPlayer) {
                loadedMagicTreeToPlayer = true;
            }
        } catch (Exception e) {
            Logger.logException(MagicTree.class, e);
        }
    }

    public void openMenuTree() {
        Message msg;
        try {
            msg = new Message(-34);
            msg.writer().writeByte(1);
            if (!isUpgrade) {
                msg.writer().writeUTF("Thu\nhoạch");
                if (this.level < MAX_LEVEL) {
                    msg.writer().writeUTF(getTextMenuUpgrade());
                }
                if (this.currPeas < this.getMaxPea()) {
                    msg.writer().writeUTF("Kết hạt\nnhanh\n" + HARVEST_GEM[this.level - 1] + " ngọc");
                    this.player.idMark.setIndexMenu(ConstNpc.MAGIC_TREE_NON_UPGRADE_LEFT_PEA);
                } else {
                    this.player.idMark.setIndexMenu(ConstNpc.MAGIC_TREE_NON_UPGRADE_FULL_PEA);
                }
            } else {
                msg.writer().writeUTF("Nâng cấp\nnhanh\n" + UPGRADE_GEM[this.level - 1] + " ngọc");
                msg.writer().writeUTF("Hủy\nnâng cấp\nhồi " + (PEA_UPGRADE[this.level - 1][3] / 2 + (this.level <= 3 ? " k" : " Tr")) + "\nvàng");
                this.player.idMark.setIndexMenu(ConstNpc.MAGIC_TREE_UPGRADE);
            }
            player.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(MagicTree.class, e);
        }
    }

    public void harvestPea() {
        if (this.currPeas > 0) {
            byte currPeasTemp = (byte) this.currPeas;
            this.currPeas = (byte) this.addPeaHarvest(this.level, this.currPeas);
            if (this.currPeas == currPeasTemp) {
                return;
            }
            this.lastTimeHarvest = System.currentTimeMillis();
            InventoryService.gI().sendItemBags(player);
            Message msg;
            try {
                msg = new Message(-34);
                msg.writer().writeByte(2);
                msg.writer().writeShort(this.currPeas);
                msg.writer().writeInt(getSecondPea());
                player.sendMessage(msg);
                msg.cleanup();
            } catch (Exception e) {
                Logger.logException(MagicTree.class, e);
            }
        }
    }

    public void showConfirmUpgradeMagicTree() {
        NpcManager.getByIdAndMap(ConstNpc.DAU_THAN, this.player.zone.map.mapId).
                createOtherMenu(player, ConstNpc.MAGIC_TREE_CONFIRM_UPGRADE, "Bạn có chắc chắn nâng cấp cây đậu?", "OK", "Từ chối");
    }

    public void showConfirmUnuppgradeMagicTree() {
        NpcManager.getByIdAndMap(ConstNpc.DAU_THAN, this.player.zone.map.mapId).
                createOtherMenu(player, ConstNpc.MAGIC_TREE_CONFIRM_UNUPGRADE, "Bạn có chắc chắn hủy nâng cấp cây đậu?", "OK", "Từ chối");
    }

    public void upgradeMagicTree() {
        short gold = PEA_UPGRADE[this.level - 1][3];
        int goldRequire = gold * (this.level <= 3 ? 1000 : 1000000);
        if (this.player.inventory.gold < goldRequire) {
            Service.gI().sendThongBao(player, "Bạn không đủ vàng để nâng cấp, còn thiếu "
                    + (goldRequire - this.player.inventory.gold) + " vàng nữa");
        } else {
            this.player.inventory.gold -= goldRequire;
            PlayerService.gI().sendInfoHpMpMoney(this.player);
            this.isUpgrade = true;
            this.lastTimeUpgrade = System.currentTimeMillis();
            this.loadMagicTree();
        }
    }

    public void unupgradeMagicTree() {
        short gold = PEA_UPGRADE[this.level - 1][3];
        int goldReturn = (gold * (this.level <= 3 ? 1000 : 1000000));
        this.player.inventory.gold += goldReturn;
        PlayerService.gI().sendInfoHpMpMoney(this.player);
        this.isUpgrade = false;
        this.loadMagicTree();
    }

    public void fastRespawnPea() {
        // Sử dụng mảng HARVEST_GEM để lấy số gem theo cấp độ
        int gemRequired = HARVEST_GEM[this.level - 1];  // Lấy số gem theo cấp độ cây

        // Kiểm tra xem người chơi có đủ gem không
        if (this.player.inventory.gem >= gemRequired) {
            // Trừ số gem yêu cầu từ túi đồ người chơi
            this.player.inventory.gem -= gemRequired;
            // Cập nhật lại thông tin gem của người chơi
            Service.gI().sendMoney(player);

            // Cập nhật số hạt đậu
            this.currPeas = this.getMaxPea();
            this.loadMagicTree();
        } else {
            // Thông báo nếu người chơi không đủ gem
            Service.gI().sendThongBao(player, "Bạn không đủ gem để kết hạt nhanh, còn thiếu "
                    + (gemRequired - this.player.inventory.gem) + " gem.");
        }
    }

    public void fastUpgradeMagicTree() {
        // Lấy số ngọc yêu cầu để nâng cấp
        int upgradeGemCost = UPGRADE_GEM[this.level - 1];

        // Kiểm tra nếu cấp độ cây còn dưới MAX_LEVEL và người chơi đủ ngọc
        if (this.level < MAX_LEVEL && this.player.inventory.gem >= upgradeGemCost) {
            // Lấy số ngọc trước khi nâng cấp
            int currentGem = this.player.inventory.gem;
           this.player.inventory.gem -= upgradeGemCost;
            this.level++;  // Tăng cấp độ cây
            this.isUpgrade = false;
            Service.gI().sendMoney(player);
             this.loadMagicTree();

            // Thông báo số ngọc đã mất
            Service.gI().sendThongBao(player, "Nâng cấp nhanh thành công! Bạn đã mất " + upgradeGemCost + " ngọc.");
        } else {
            // Thông báo nếu người chơi không đủ ngọc hoặc cây đã đạt cấp tối đa
            Service.gI().sendThongBao(player, "Bạn không đủ ngọc để nâng cấp nhanh hoặc cây đã đạt cấp tối đa.");
        }
    }

    private byte getMaxPea() {
        return (byte) ((this.level - 1) * 2 + 5);
    }

    private short getSecondPerPea() {
        return (short) (this.level * 60);
    }

    private int getSecondPea() {
        short secondPerPea = (short) getSecondPerPea();
        long timePeaRelease = lastTimeHarvest + secondPerPea * 1000;
        int secondLeft = (int) ((timePeaRelease - System.currentTimeMillis()) / 1000);
        return secondLeft < 0 ? 0 : secondLeft;
    }

    private int getSecondUpgrade() {
        return (int) ((lastTimeUpgrade + getTimeUpgrade() - System.currentTimeMillis()) / 1000);
    }

    private String getTextMenuUpgrade() {
        String text = "Nâng cấp\n";
        short d = PEA_UPGRADE[this.level - 1][0];
        short h = PEA_UPGRADE[this.level - 1][1];
        short m = PEA_UPGRADE[this.level - 1][2];
        short gold = PEA_UPGRADE[this.level - 1][3];
        if (d != 0) {
            text += d + "d";
        }
        if (h != 0) {
            text += h + "h";
        }
        if (m != 0) {
            text += m + "'";
        }
        text += "\n" + gold + (this.level <= 3 ? " k" : " Tr") + "\nvàng";
        return text;
    }

    private long getTimeUpgrade() {
        short d = PEA_UPGRADE[this.level - 1][0];
        short h = PEA_UPGRADE[this.level - 1][1];
        short m = PEA_UPGRADE[this.level - 1][2];
        return d * 24 * 60 * 60 * 1000L + h * 60 * 60 * 1000L + m * 60 * 1000L;
    }

    public void dispose() {
        this.player = null;
    }

    public int addPeaHarvest(byte level, int quantity) {
        Item pea = ItemService.gI().createNewItem(MagicTree.PEA_TEMP[level - 1], quantity);
        pea.itemOptions.add(new Item.ItemOption(level - 1 > 1 ? 2 : 48, MagicTree.PEA_PARAM[level - 1]));
        InventoryService.gI().addItemBag(player, pea);
        if (pea.quantity > 0) {
            InventoryService.gI().addItemBox(player, pea);
        }
        if (pea.quantity < quantity) {
            Service.gI().sendThongBao(player, "Bạn vừa thu hoạch được " + (quantity - pea.quantity) + " hạt " + pea.template.name);
        }
        return pea.quantity;
    }

}
