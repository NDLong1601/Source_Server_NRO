package nro.models.services.shenron;

import nro.models.services.shenron.SummonDragon;
import nro.models.network.Message;
import nro.models.consts.ConstNpc;
import nro.models.database.MrBlue;
import nro.models.database.PlayerDAO;
import nro.models.item.Item;
import java.util.List;
import nro.models.map.Zone;
import nro.models.player.Player;
import nro.models.server.Client;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.map.service.NpcService;
import nro.models.services.Service;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 *
 */
public class SummonDragonNamek {

    public static final byte DRAGON_PORUNGA = 1;
    private static SummonDragonNamek instance;

    public static final byte WISHED = 0;
    public static final byte TIME_UP = 1;
    private boolean isShenronAppear;
    public Player playerSummonShenron;
    private int playerSummonShenronId;
    private Zone mapShenronAppear;
    private int menuShenron;
    private byte select;
    private final Thread update;
    private boolean active;
    public boolean isPlayerDisconnect;
    private long lastTimeShenronWait;
    private final int timeShenronWait = 300000;

    public static SummonDragonNamek gI() {
        if (instance == null) {
            instance = new SummonDragonNamek();
        }
        return instance;
    }

    private SummonDragonNamek() {
        this.update = new Thread(() -> {
            while (active) {
                try {
                    if (isShenronAppear) {
                        if (isPlayerDisconnect) {
                            List<Player> players = mapShenronAppear.getPlayers();
                            for (Player plMap : players) {
                                if (plMap.isPl() && plMap.id == playerSummonShenronId) {
                                    playerSummonShenron = plMap;
                                    reSummonShenron();
                                    isPlayerDisconnect = false;
                                    break;
                                }
                            }

                        }
                        if (Util.canDoWithTime(lastTimeShenronWait, timeShenronWait)) {
                            shenronLeave(playerSummonShenron, TIME_UP);
                        }
                    }
                    Thread.sleep(1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        this.active();
    }

    private void active() {
        if (!active) {
            active = true;
            this.update.start();
        }
    }

    public void summonNamec(Player pl) {
        if (pl.zone.map.mapId == 7) {
            playerSummonShenron = pl;
            playerSummonShenronId = (int) pl.id;
            mapShenronAppear = pl.zone;
            lastTimeShenronWait = System.currentTimeMillis();
            sendNotifyShenronNamekAppear();
            activeShenron(pl, true, DRAGON_PORUNGA);
            sendBlackGokuhesNamec(pl);
        } else {
            Service.gI().sendThongBao(pl, "Không thể thực hiện");
        }
    }

    private void reSummonShenron() {
        activeShenron(playerSummonShenron, true, DRAGON_PORUNGA);
        sendBlackGokuhesNamec(playerSummonShenron);
    }

    private void activeShenron(Player pl, boolean appear, byte type) {
        Message msg;
        try {
            msg = new Message(-83);
            msg.writer().writeByte(appear ? 0 : (byte) 1);
            if (appear) {
                msg.writer().writeShort(pl.zone.map.mapId);
                msg.writer().writeShort(pl.zone.map.bgId);
                msg.writer().writeByte(pl.zone.zoneId);
                msg.writer().writeInt((int) pl.id);
                msg.writer().writeUTF("null");
                msg.writer().writeShort(pl.location.x);
                msg.writer().writeShort(pl.location.y);
                msg.writer().writeByte(type);
                isShenronAppear = true;
            }
            Service.gI().sendMessAllPlayer(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendNotifyShenronNamekAppear() {
        Message msg = null;
        try {
            msg = new Message(-25);
            msg.writer().writeUTF(playerSummonShenron.name + " vừa gọi rồng thần namek tại "
                    + playerSummonShenron.zone.map.mapName + " khu vực " + playerSummonShenron.zone.zoneId);
            Service.gI().sendMessAllPlayerIgnoreMe(playerSummonShenron, msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void confirmWish() {
        switch (this.menuShenron) {
            case ConstNpc.SHOW_SHENRON_NAMEK_CONFIRM:
                try {
                switch (select) {
                    case 0:
                        if (playerSummonShenron.clan != null) {
                            playerSummonShenron.clan.members.forEach(m -> {
                                if (Client.gI().getPlayer(m.id) != null) {
                                    Player p = Client.gI().getPlayer(m.id);
                                    Item it = ItemService.gI().createNewItem((short) 16);
                                    it.quantity = Util.nextInt(1, 20);
                                    InventoryService.gI().addItemBag(p, it);
                                    InventoryService.gI().sendItemBags(p);
                                } else {
                                    Player p = MrBlue.loadById(m.id);
                                    if (p != null) {
                                        Item it = ItemService.gI().createNewItem((short) 16);
                                        it.quantity = Util.nextInt(1, 20);
                                        InventoryService.gI().addItemBag(p, it);
                                        PlayerDAO.updatePlayer(p);
                                    }
                                }
                            });
                        } else {
                            Item it = ItemService.gI().createNewItem((short) 16);
                            it.quantity = Util.nextInt(1, 20);
                            InventoryService.gI().addItemBag(playerSummonShenron, it);
                            InventoryService.gI().sendItemBags(playerSummonShenron);
                        }
                        break;
                    case 1:
                        if (playerSummonShenron.clan != null) {
                            playerSummonShenron.clan.members.forEach(m -> {
                                if (Client.gI().getPlayer(m.id) != null) {
                                    Player p = Client.gI().getPlayer(m.id);
                                    byte[] option = {77, 80, 81, 103, 50, 94, 5};
                                    byte[] option_v2 = {14, 16, 17, 19, 27, 28, 5, 47, 87}; //77 %hp // 80 //81 //103 //50 //94 //5 % sdcm
                                    byte optionid = 0;
                                    byte optionid_v2 = 0;
                                    byte param = 0;
                                    Item it = ItemService.gI().createNewItem((short) 942);
                                    it.itemOptions.clear();
                                    optionid = option[Util.nextInt(0, 6)];
                                    param = (byte) Util.nextInt(5, 10);
                                    it.itemOptions.add(new Item.ItemOption(optionid, param));
                                    if (Util.isTrue(20, 100)) {
                                        optionid_v2 = option_v2[Util.nextInt(option_v2.length)];
                                        it.itemOptions.add(new Item.ItemOption(optionid_v2, param));
                                    }
                                    it.itemOptions.add(new Item.ItemOption(30, 0));
                                    it.quantity = 1;
                                    InventoryService.gI().addItemBag(p, it);
                                    InventoryService.gI().sendItemBags(p);
                                } else {
                                    Player p = MrBlue.loadById(m.id);
                                    if (p != null) {
                                        byte[] option = {77, 80, 81, 103, 50, 94, 5};
                                        byte[] option_v2 = {14, 16, 17, 19, 27, 28, 5, 47, 87}; //77 %hp // 80 //81 //103 //50 //94 //5 % sdcm
                                        byte optionid = 0;
                                        byte optionid_v2 = 0;
                                        byte param = 0;
                                        Item it = ItemService.gI().createNewItem((short) 942);
                                        it.itemOptions.clear();
                                        optionid = option[Util.nextInt(0, 6)];
                                        param = (byte) Util.nextInt(5, 10);
                                        it.itemOptions.add(new Item.ItemOption(optionid, param));
                                        if (Util.isTrue(20, 100)) {
                                            optionid_v2 = option_v2[Util.nextInt(option_v2.length)];
                                            it.itemOptions.add(new Item.ItemOption(optionid_v2, param));
                                        }
                                        it.itemOptions.add(new Item.ItemOption(30, 0));
                                        it.quantity = 1;
                                        InventoryService.gI().addItemBag(p, it);
                                        PlayerDAO.updatePlayer(p);
                                    }
                                }
                            });
                        } else {
                            byte[] option = {77, 80, 81, 103, 50, 94, 5};
                            byte[] option_v2 = {14, 16, 17, 19, 27, 28, 5, 47, 87}; //77 %hp // 80 //81 //103 //50 //94 //5 % sdcm
                            byte optionid = 0;
                            byte optionid_v2 = 0;
                            byte param = 0;
                            Item it = ItemService.gI().createNewItem((short) 942);
                            it.itemOptions.clear();
                            optionid = option[Util.nextInt(0, 6)];
                            param = (byte) Util.nextInt(5, 10);
                            it.itemOptions.add(new Item.ItemOption(optionid, param));
                            if (Util.isTrue(20, 100)) {
                                optionid_v2 = option_v2[Util.nextInt(option_v2.length)];
                                it.itemOptions.add(new Item.ItemOption(optionid_v2, param));
                            }
                            it.itemOptions.add(new Item.ItemOption(30, 0));
                            it.quantity = 1;
                            InventoryService.gI().addItemBag(playerSummonShenron, it);
                            InventoryService.gI().sendItemBags(playerSummonShenron);
                        }
                        break;
                    case 2:
                        if (playerSummonShenron.clan != null) {
                            playerSummonShenron.clan.members.forEach(m -> {
                                if (Client.gI().getPlayer(m.id) != null) {
                                    Player p = Client.gI().getPlayer(m.id);
                                    Item it = ItemService.gI().createNewItem((short) 2053);
                                    it.quantity = 99;
                                    InventoryService.gI().addItemBag(p, it);
                                    InventoryService.gI().sendItemBags(p);
                                } else {
                                    Player p = MrBlue.loadById(m.id);
                                    if (p != null) {
                                        Item it = ItemService.gI().createNewItem((short) 2053);
                                        it.quantity = 99;
                                        InventoryService.gI().addItemBag(p, it);
                                        PlayerDAO.updatePlayer(p);
                                    }
                                }
                            });
                        } else {
                            Item it = ItemService.gI().createNewItem((short) 2053);
                            it.quantity = 99;
                            InventoryService.gI().addItemBag(playerSummonShenron, it);
                            InventoryService.gI().sendItemBags(playerSummonShenron);
                        }
                        break;
                    default:
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            break;
        }
        shenronLeave(this.playerSummonShenron, WISHED);
    }

    public void showConfirmShenron(Player pl, int menu, byte select) {
        this.menuShenron = menu;
        this.select = select;
        String wish = null;
        switch (menu) {
            case ConstNpc.SHOW_SHENRON_NAMEK_CONFIRM:
                switch (select) {
                    case 0:
                        wish = "1-20 viên ngọc rồng 3 sao";
                        break;
                    case 1:
                        wish = "pet hổ sẽ béo";
                        break;
                    case 2:
                        wish = "x99 bột mỳ";
                        break;
                }
                break;
        }
        NpcService.gI().createMenuRongThieng(pl, ConstNpc.SHENRON_NAMEK_CONFIRM, "Ngươi có chắc muốn ước?", wish, "Từ chối");
    }

    public void sendBlackGokuhesNamec(Player pl) {
        NpcService.gI().createMenuRongThieng(pl, ConstNpc.SHOW_SHENRON_NAMEK_CONFIRM, "Ta sẽ ban cho cả bang hội ngươi 1 điều ước, ngươi có 5 phút, hãy suy nghĩ thật kỹ trước khi quyết định", "1-20 viên ngọc rồng 3 sao", "pet hổ sẽ béo", "x99 bột mỳ");
    }

    public void shenronLeave(Player pl, byte type) {
        if (type == WISHED) {
            //Điều ước Bùa mạnh mẽ cho tất cả trong 7 ngày của các con đã được thực hiện...tạm biệt
            NpcService.gI().createTutorial(pl, 0, "Điều ước của ngươi đã được thực hiện...tạm biệt");
        } else {
            NpcService.gI().createMenuRongThieng(pl, ConstNpc.IGNORE_MENU, "Ta buồn ngủ quá rồi\nHẹn gặp ngươi lần sau, ta đi đây, bái bai");
        }
        activeShenron(pl, false, SummonDragon.DRAGON_SHENRON);
        this.isShenronAppear = false;
        this.menuShenron = -1;
        this.select = -1;
        this.playerSummonShenron = null;
        this.playerSummonShenronId = -1;
        this.mapShenronAppear = null;
    }
}
