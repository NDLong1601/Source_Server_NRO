package nro.models.services_func;

import nro.models.clan.Clan;
import nro.models.clan.ClanMember;
import nro.models.data.LocalManager;
import nro.models.consts.ConstNpc;
import nro.models.database.PlayerDAO;
import nro.models.item.Item;
import nro.models.map.Zone;
import nro.models.npc.Npc;
import nro.models.map.service.NpcManager;
import nro.models.player.Player;
import nro.models.network.Message;
import nro.models.interfaces.ISession;
import nro.models.item.Item.ItemOption;
import nro.models.server.Client;
import nro.models.services.Service;
import nro.models.services.GiftCodeService;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.map.service.NpcService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nro.models.Bot.Bot;
import nro.models.Bot.BotAttackplayer;
import nro.models.Bot.BotManager;
import nro.models.Bot.NewBot;
import nro.models.Bot.BotGiaoDich;
import nro.models.consts.ConstTaskBadges;
import nro.models.player.Inventory;
import nro.models.server.Manager;
import nro.models.services.ClanService;
import nro.models.map.service.ChangeMapService;
import nro.models.services.PlayerService;
import nro.models.server.ServerLog;
import nro.models.task.BadgesTaskService;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 *
 */
public class Input {

    private static final Map<Integer, Object> PLAYER_ID_OBJECT = new HashMap<>();

    public static final int CHANGE_PASSWORD = 500;
    public static final int GIFT_CODE = 501;
    public static final int FIND_PLAYER = 502;
    public static final int FIND_PLAYER_NAME = 502992;
    public static final int CHANGE_NAME = 503;
    public static final int CHOOSE_LEVEL_BDKB = 504;
    public static final int NAP_THE = 505;
    public static final int CHANGE_NAME_BY_ITEM = 506;
    public static final int GIVE_IT = 507;
    public static final int GET_IT = 508;
    public static final int DANGKY = 509;
    public static final int TRADE_GOLD = 5100;
    public static final int TRADE_GEM = 5101;
    public static final int CHOOSE_LEVEL_KGHD = 510;
    public static final int CHOOSE_LEVEL_CDRD = 511;
    public static final int DISSOLUTION_CLAN = 513;
    public static final int TANG_NGOC_HONG = 514;
    public static final int SELECT_LUCKYNUMBER = 514;
    public static final byte NUMERIC = 0;
    public static final byte ANY = 1;
    public static final byte NUMBER = 81;
    public static final byte PASSWORD = 2;
    public static final byte MBV = 23;
    public static final byte BANSLL = 24;
    public static final byte BANGHOI = 25;
    public static final int CON_SO_MAY_MAN_NGOC = 26;
    public static final int CON_SO_MAY_MAN_VANG = 27;
    public static final int TRONG_DUA = 28;
    public static final int SEND_ITEM_OP = 29;
    public static final int BOTQUAI = 30;
    public static final int BOTITEM = 31;
    public static final int BOTBOSS = 32;
    public static final int BOTATTACKPLAYER = 33;
    public static final int FIND_PLAYER_GIFT_RUBY = 34;

    private static Input intance;

    private Input() {

    }

    public static Input gI() {
        if (intance == null) {
            intance = new Input();
        }
        return intance;
    }

    public void doInput(Player player, Message msg) {
        try {
            String[] text = new String[msg.reader().readByte()];
            for (int i = 0; i < text.length; i++) {
                text[i] = msg.reader().readUTF();
            }
            switch (player.idMark.getTypeInput()) {
                case BOTITEM -> {
                    int slot = Integer.parseInt(text[0]);
                    int idBan = Integer.parseInt(text[1]);
                    int idTraoDoi = Integer.parseInt(text[2]);
                    int slot_TraoDoi = Integer.parseInt(text[3]);
                    BotGiaoDich bs = new BotGiaoDich(idBan, idTraoDoi, slot_TraoDoi);
                    new Thread(() -> {
                        NewBot.gI().runBot(1, bs, slot);
                    }).start();
                }
                case BOTBOSS -> {
                    int slot = Integer.parseInt(text[0]);
                    new Thread(() -> {
                        NewBot.gI().runBot(2, null, slot);
                    }).start();
                }
                case BOTQUAI -> {
                    int slot = Integer.parseInt(text[0]);
                    new Thread(() -> {
                        NewBot.gI().runBot(0, null, slot);
                    }).start();
                }
                case BOTATTACKPLAYER -> {
                    try {
                        int slot = Integer.parseInt(text[0]);
                        if (slot >= 0 && slot < BotManager.gI().bot.size()) {
                            Bot bot = BotManager.gI().bot.get(slot);
                            if (bot instanceof BotAttackplayer) {
                                // Nếu muốn chạy trong thread riêng (không bắt buộc)
                                new Thread(() -> {
                                    try {
                                        //    ((BotAttackplayer) bot).targetPlayer
                                    } catch (Exception e) {
                                    }
                                }).start();
                            }
                        }
                    } catch (NumberFormatException e) {
                    }
                }

                case TRADE_GOLD -> {
                    int cuantity1 = Integer.parseInt(text[0]);
                    if (!player.getSession().actived) {
                        Service.gI().sendThongBao(player, "Vui lòng kích hoạt tài khoản!");
                        break;
                    }
                    if (cuantity1 < 10000 || cuantity1 > 5_000_000) {
                        Service.gI().sendThongBao(player, "Tối thiểu 10.000Đ và tối đa 5.000.000Đ");
                        break;
                    }
                    if (player.getSession().vnd < cuantity1) {
                        Service.gI().sendThongBao(player, "Số dư không đủ, vui lòng nạp thêm");
                    } else {
                        PlayerDAO.subvnd(player, cuantity1);

                        int soLuongThoiVang = (cuantity1 / 1000) * 4;
                        Item item457 = ItemService.gI().createNewItem((short) 457, soLuongThoiVang);
                        InventoryService.gI().addItemBag(player, item457);

                        int soLuongVe = (cuantity1 / 10000) * 10;
                        Item item718 = ItemService.gI().createNewItem((short) 718, soLuongVe);
                        InventoryService.gI().addItemBag(player, item718);

                        InventoryService.gI().sendItemBags(player);

                        BadgesTaskService.updateCountBagesTask(player, ConstTaskBadges.DAI_GIA_MOI_NHU, cuantity1);
                        BadgesTaskService.updateCountBagesTask(player, ConstTaskBadges.EM_XINH_EM_DEP, cuantity1);

                        int eventPointBonus = (cuantity1 / 10000) * 50;
                        player.event.addEventPoint(eventPointBonus);

                        Service.gI().sendThongBao(player, "Bạn nhận thêm " + eventPointBonus + " điểm sự kiện!");
                        Service.gI().sendThongBao(player, "Đã đổi thành công. Bạn nhận được " + soLuongThoiVang + " thỏi vàng và " + soLuongVe + " vé tặng ngọc.");
                    }
                }

                case TRADE_GEM -> {
                    int quantity = Integer.parseInt(text[0]);
                    if (quantity < 10000 || quantity > 5_000_000) {
                        Service.gI().sendThongBao(player, "Tối thiểu 10.000Đ và tối đa 5.000.000Đ");
                        break;
                    }
                    if (player.getSession().vnd < quantity) {
                        Service.gI().sendThongBao(player, "Số dư không đủ, vui lòng nạp thêm");
                    } else {
                        PlayerDAO.subvnd(player, quantity);

                        player.inventory.gem += quantity;
                        Service.gI().sendMoney(player);

                        int soLuongVe = (quantity / 10000) * 10;
                        Item item718 = ItemService.gI().createNewItem((short) 718, soLuongVe);
                        InventoryService.gI().addItemBag(player, item718);

                        InventoryService.gI().sendItemBags(player);

                        BadgesTaskService.updateCountBagesTask(player, ConstTaskBadges.DAI_GIA_MOI_NHU, quantity);
                        BadgesTaskService.updateCountBagesTask(player, ConstTaskBadges.EM_XINH_EM_DEP, quantity);

                        int eventPointBonus = (quantity / 10000) * 50;
                        player.event.addEventPoint(eventPointBonus);

                        Service.gI().sendThongBao(player, "Bạn nhận thêm " + eventPointBonus + " điểm sự kiện!");
                        Service.gI().sendThongBao(player, "Đã nạp thành công. Bạn nhận được " + quantity + " ngọc và " + soLuongVe + " vé tặng ngọc.");
                    }
                }

                case SEND_ITEM_OP -> {
                    if (player.isAdmin()) {
                        int idItemBuff = Integer.parseInt(text[1]);
                        int idOptionBuff = Integer.parseInt(text[2]);
                        int slOptionBuff = Integer.parseInt(text[3]);
                        int slItemBuff = Integer.parseInt(text[4]);
                        Player pBuffItem = Client.gI().getPlayer(text[0]);

                        if (pBuffItem != null) {
                            String txtBuff = "Buff to player: " + pBuffItem.name + "\b";

                            switch (idItemBuff) {
                                case -1:
                                    pBuffItem.inventory.gold = Math.min(pBuffItem.inventory.gold + (long) slItemBuff, Inventory.LIMIT_GOLD);
                                    txtBuff += slItemBuff + " vàng\b";
                                    Service.gI().sendMoney(pBuffItem);
                                    ServerLog.logAdmin(pBuffItem.name, slItemBuff);
                                    break;
                                case -2:
                                    pBuffItem.inventory.gem = Math.min(pBuffItem.inventory.gem + slItemBuff, 2000000000);
                                    txtBuff += slItemBuff + " ngọc\b";
                                    Service.gI().sendMoney(pBuffItem);
                                    ServerLog.logAdmin(pBuffItem.name, slItemBuff);
                                    break;
                                case -3:
                                    pBuffItem.inventory.ruby = Math.min(pBuffItem.inventory.ruby + slItemBuff, 2000000000);
                                    txtBuff += slItemBuff + " ngọc khóa\b";
                                    Service.gI().sendMoney(pBuffItem);
                                    ServerLog.logAdmin(pBuffItem.name, slItemBuff);
                                    break;
                                default:
                                    Item itemBuffTemplate = ItemService.gI().createNewItem((short) idItemBuff);
                                    itemBuffTemplate.itemOptions.add(new ItemOption(idOptionBuff, slOptionBuff));
                                    itemBuffTemplate.quantity = slItemBuff;

                                    if (InventoryService.gI().addItemBag(pBuffItem, itemBuffTemplate)) {
                                        txtBuff += "x" + slItemBuff + " " + itemBuffTemplate.template.name + "\b";
                                        ServerLog.logAdmin(pBuffItem.name, slItemBuff);
                                        InventoryService.gI().sendItemBags(pBuffItem);
                                    } else {
                                        Service.gI().sendThongBao(player, "Không thể thêm vật phẩm vào hành trang của người chơi");
                                        return;
                                    }
                                    break;
                            }

                            NpcService.gI().createTutorial(player, 24, txtBuff);
                            if (player.id != pBuffItem.id) {
                                NpcService.gI().createTutorial(player, 24, txtBuff);
                            }
                        } else {
                            Service.gI().sendThongBao(player, "Player không online");
                        }
                        break;
                    }
                }
                case CON_SO_MAY_MAN_NGOC -> {
                    int CSMM2 = Integer.parseInt(text[0]);
                    if (CSMM2 >= MiniGame.gI().MiniGame_S1_Gem.min && CSMM2 <= MiniGame.gI().MiniGame_S1_Gem.max && MiniGame.gI().MiniGame_S1_Gem.second > 10) {
                        MiniGame.gI().MiniGame_S1_Gem.newData(player, CSMM2);
                    } else {
                        Service.gI().sendThongBao(player, "Số bạn chọn không hợp lệ hoặc đã hết thời gian đặt cược.");
                    }
                }
                case CON_SO_MAY_MAN_VANG -> {
                    int CSMM2 = Integer.parseInt(text[0]);
                    if (CSMM2 >= MiniGame.gI().MiniGame_S1_Gold.min && CSMM2 <= MiniGame.gI().MiniGame_S1_Gold.max && MiniGame.gI().MiniGame_S1_Gold.second > 10) {
                        MiniGame.gI().MiniGame_S1_Gold.newData(player, CSMM2);
                    } else {
                        Service.gI().sendThongBao(player, "Số bạn chọn không hợp lệ hoặc đã hết thời gian đặt cược.");
                    }
                }
                case GIVE_IT -> {
                    String name = text[0];
                    int id = Integer.parseInt(text[1]);
                    int op = Integer.parseInt(text[2]);
                    int pr = Integer.parseInt(text[3]);
                    int q = Integer.parseInt(text[4]);

                    if (Client.gI().getPlayer(name) != null) {
                        Item item = ItemService.gI().createNewItem(((short) id));
                        List<Item.ItemOption> ops = ItemService.gI().getListOptionItemShop((short) id);
                        if (!ops.isEmpty()) {
                            item.itemOptions = ops;
                        }
                        item.quantity = q;
                        item.itemOptions.add(new Item.ItemOption(op, pr));
                        InventoryService.gI().addItemBag(Client.gI().getPlayer(name), item);
                        InventoryService.gI().sendItemBags(Client.gI().getPlayer(name));
                        Service.gI().sendThongBao(Client.gI().getPlayer(name), "Nhận " + item.template.name + " từ " + player.name);

                    } else {
                        Service.gI().sendThongBao(player, "Không online");
                    }
                }
                case GET_IT -> {
                    int id = Integer.parseInt(text[0]);
                    int op = Integer.parseInt(text[1]);
                    int pr = Integer.parseInt(text[2]);
                    int q = Integer.parseInt(text[3]);
                    if (player.isAdmin()) {
                        Item item = ItemService.gI().createNewItem(((short) id));
                        List<Item.ItemOption> ops = ItemService.gI().getListOptionItemShop((short) id);
                        if (!ops.isEmpty()) {
                            item.itemOptions = ops;
                        }
                        item.quantity = q;
                        item.itemOptions.add(new Item.ItemOption(op, pr));
                        InventoryService.gI().addItemBag(player, item);
                        InventoryService.gI().sendItemBags(player);
                        Service.gI().sendThongBao(player, "Nhận " + item.template.name + " !");

                    } else {
                        Service.gI().sendThongBao(player, "Không đủ quyền hạn!");
                    }
                }
                case CHANGE_PASSWORD ->
                    Service.gI().changePassword(player, text[0], text[1], text[2]);
                case GIFT_CODE ->
                    GiftCodeService.gI().giftCode(player, text[0]);
                case FIND_PLAYER -> {
                    Player pl = Client.gI().getPlayer(text[0]);
                    if (pl != null) {
                        NpcService.gI().createMenuConMeo(player, ConstNpc.MENU_FIND_PLAYER, -1, "Ngài muốn..?",
                                new String[]{"Đi tới\n" + pl.name, "Gọi " + pl.name + "\ntới đây", "Đổi tên", "Ban", "Kick"},
                                pl);
                    } else {
                        Service.gI().sendThongBao(player, "Người chơi không tồn tại hoặc đang offline");
                    }
                }
                case FIND_PLAYER_NAME -> {
                    Player target = Client.gI().getPlayer(text[0]);
                    if (target != null) {
                        player.menuPlayer = target;
                        Input.gI().createForm(player, FIND_PLAYER_GIFT_RUBY, "Nhập số ngọc bạn muốn tặng cho " + target.name,
                                new SubInput("Số ngọc", ANY));
                    } else {
                        Service.gI().sendThongBao(player, "Người chơi không tồn tại hoặc đang offline");
                    }
                }
                case FIND_PLAYER_GIFT_RUBY -> {
                    Player target = player.menuPlayer;
                    if (target != null) {
                        try {
                            int soGem = Integer.parseInt(text[0]);
                            if (soGem <= 0) {
                                Service.gI().sendThongBao(player, "Số ngọc xanh không hợp lệ");
                                return;
                            }

                            int phi = (int) (soGem * 0.1); // Tính phí 10%
                            int tongGem = soGem + phi;     // Tổng cần trừ

                            if (player.inventory.gem < tongGem) {
                                Service.gI().sendThongBao(player, "Bạn cần " + tongGem + " ngọc xanh để tặng (bao gồm phí 10%)");
                                return;
                            }

                            Item item718 = InventoryService.gI().findItemBag(player, 718);
                            if (item718 == null || item718.quantity < 1) {
                                Service.gI().sendThongBao(player, "Bạn cần 1 vé để tặng ngọc xanh");
                                return;
                            }

                            player.inventory.gem -= tongGem;
                            InventoryService.gI().subQuantityItemsBag(player, item718, 1);
                            InventoryService.gI().sendItemBags(player);

                            int gemNhan = soGem;
                            int gemThucNhan = (int) (gemNhan * 0.9);
                            target.inventory.gem += gemThucNhan;

                            Service.gI().sendMoney(player);
                            Service.gI().sendMoney(target);

                            Service.gI().sendThongBao(player, "Bạn đã tặng " + gemThucNhan + " ngọc xanh cho " + target.name + " (đã trừ phí " + phi + ")");
                            Service.gI().sendThongBao(target, player.name + " vừa tặng bạn " + gemThucNhan + " ngọc xanh");
                        } catch (Exception e) {
                            Service.gI().sendThongBao(player, "Lỗi định dạng số lượng ngọc xanh");
                        }
                    } else {
                        Service.gI().sendThongBao(player, "Người chơi không hợp lệ hoặc đã offline");
                    }
                }
                case CHANGE_NAME -> {
                    Player plChanged = (Player) PLAYER_ID_OBJECT.get((int) player.id);
                    if (plChanged != null) {
                        if (LocalManager.executeQuery("select * from player where name = ?", text[0]).next()) {
                            Service.gI().sendThongBao(player, "Tên nhân vật đã tồn tại");
                        } else {
                            plChanged.name = text[0];
                            LocalManager.executeUpdate("update player set name = ? where id = ?", plChanged.name, plChanged.id);
                            Service.gI().player(plChanged);
                            Service.gI().Send_Caitrang(plChanged);
                            Service.gI().sendFlagBag(plChanged);
                            Zone zone = plChanged.zone;
                            ChangeMapService.gI().changeMap(plChanged, zone, plChanged.location.x, plChanged.location.y);
                            Service.gI().sendThongBao(plChanged, "Chúc mừng bạn đã có cái tên mới đẹp đẽ hơn tên ban đầu");
                            Service.gI().sendThongBao(player, "Đổi tên người chơi thành công");
                        }
                    }
                }
                case CHANGE_NAME_BY_ITEM -> {
                    if (player != null) {
                        if (LocalManager.executeQuery("select * from player where name = ?", text[0]).next()) {
                            Service.gI().sendThongBao(player, "Tên nhân vật đã tồn tại");
                            createFormChangeNameByItem(player);
                        } else if (Util.haveSpecialCharacter(text[0])) {
                            Service.gI().sendThongBaoOK(player, "Tên nhân vật không được chứa ký tự đặc biệt");
                        } else if (text[0].length() < 5) {
                            Service.gI().sendThongBaoOK(player, "Tên nhân vật quá ngắn");
                        } else if (text[0].length() > 10) {
                            Service.gI().sendThongBaoOK(player, "Tên nhân vật chỉ đồng ý các ký tự a-z, 0-9 và chiều dài từ 5 đến 10 ký tự");
                        } else {
                            Item theDoiTen = InventoryService.gI().findItem(player.inventory.itemsBag, 2006);
                            if (theDoiTen == null) {
                                Service.gI().sendThongBao(player, "Không tìm thấy thẻ đổi tên");
                            } else {
                                InventoryService.gI().subQuantityItemsBag(player, theDoiTen, 1);
                                player.name = text[0].toLowerCase();
                                LocalManager.executeUpdate("update player set name = ? where id = ?", player.name, player.id);
                                Service.gI().player(player);
                                Service.gI().Send_Caitrang(player);
                                Service.gI().sendFlagBag(player);
                                Zone zone = player.zone;
                                ChangeMapService.gI().changeMap(player, zone, player.location.x, player.location.y);
                                Service.gI().sendThongBao(player, "Chúc mừng bạn đã có cái tên mới đẹp đẽ hơn tên ban đầu");
                            }
                        }
                    }
                }
                case CHOOSE_LEVEL_BDKB -> {
                    int level = Integer.parseInt(text[0]);
                    if (level >= 1 && level <= 110) {
                        Npc npc = NpcManager.getByIdAndMap(ConstNpc.QUY_LAO_KAME, player.zone.map.mapId);
                        if (npc != null) {
                            npc.createOtherMenu(player, ConstNpc.MENU_ACCEPT_GO_TO_BDKB,
                                    "Con có chắc muốn đến\nhang kho báu cấp độ " + level + " ?",
                                    new String[]{"Đồng ý", "Từ chối"}, level);
                        }
                    } else {
                        Service.gI().sendThongBao(player, "Không thể thực hiện");
                    }
                }
                case CHOOSE_LEVEL_KGHD -> {
                    int level = Integer.parseInt(text[0]);
                    if (level >= 1 && level <= 110) {
                        Npc npc = NpcManager.getByIdAndMap(ConstNpc.MR_POPO, player.zone.map.mapId);
                        if (npc != null) {
                            npc.createOtherMenu(player, 2,
                                    "Cậu có chắc muốn đến\nDestron Gas cấp độ " + level + " ?",
                                    new String[]{"Đồng ý", "Từ chối"}, level);
                        }
                    }
                }
                case CHOOSE_LEVEL_CDRD -> {
                    int level = Integer.parseInt(text[0]);
                    if (level >= 1 && level <= 110) {
                        Npc npc = NpcManager.getByIdAndMap(ConstNpc.THAN_VU_TRU, player.zone.map.mapId);
                        if (npc != null) {
                            npc.createOtherMenu(player, 3,
                                    "Con có chắc muốn đến\ncon đường rắn độc cấp độ " + level + " ?",
                                    new String[]{"Đồng ý", "Từ chối"}, level);
                        }
                    }
                }
                case MBV -> {
                    int mbv = Integer.parseInt(text[0]);
                    int nmbv = Integer.parseInt(text[1]);
                    int rembv = Integer.parseInt(text[2]);
                    if ((mbv + "").length() != 6 || (nmbv + "").length() != 6 || (rembv + "").length() != 6) {
                        Service.gI().sendThongBao(player, "Trêu bố mày à?");
                    } else if (player.mbv == 0) {
                        Service.gI().sendThongBao(player, "Bạn chưa cài mã bảo vệ!");
                    } else if (player.mbv != mbv) {
                        Service.gI().sendThongBao(player, "Mã bảo vệ không đúng");
                    } else if (nmbv != rembv) {
                        Service.gI().sendThongBao(player, "Mã bảo vệ không trùng khớp");
                    } else {
                        player.mbv = nmbv;
                        Service.gI().sendThongBao(player, "Đổi mã bảo vệ thành công!");
                    }
                }
                case BANSLL -> {
                    int sltv = Math.abs(Integer.parseInt(text[0]));
                    long cost = (long) sltv * 37000000;
                    Item ThoiVang = InventoryService.gI().findItemBag(player, 457);
                    if (ThoiVang != null) {
                        if (ThoiVang.quantity < sltv) {
                            Service.gI().sendThongBao(player, "Bạn chỉ có " + ThoiVang.quantity + " Thỏi vàng");
                        } else {
                            if (player.inventory.gold + cost > Inventory.LIMIT_GOLD) {
                                int slban = (int) ((Inventory.LIMIT_GOLD - player.inventory.gold) / 37000000);
                                if (slban < 1) {
                                    Service.gI().sendThongBao(player, "Vàng sau khi bán vượt quá giới hạn");
                                } else if (slban < 2) {
                                    Service.gI().sendThongBao(player, "Bạn chỉ có thể bán 1 Thỏi vàng");
                                } else {
                                    Service.gI().sendThongBao(player, "Số lượng trong khoảng 1 tới " + slban);
                                }
                            } else {
                                InventoryService.gI().subQuantityItemsBag(player, ThoiVang, sltv);
                                InventoryService.gI().sendItemBags(player);
                                player.inventory.gold += cost;
                                Service.gI().sendMoney(player);
                                Service.gI().sendThongBao(player, "Đã bán " + sltv + " Thỏi vàng thu được " + Util.numberToMoney(cost) + " vàng");
                            }
                        }
                    }
                }
                case TANG_NGOC_HONG -> {
                    Player pl = Client.gI().getPlayer(text[0]);
                    int numruby = Integer.parseInt((text[1]));
                    if (pl != null) {
                        if (numruby > 0 && player.inventory.ruby >= numruby) {
                            Item item = InventoryService.gI().findItemBag(player, 2002);
                            player.inventory.subGem(numruby);
                            PlayerService.gI().sendInfoHpMpMoney(player);
                            pl.inventory.ruby += numruby;
                            PlayerService.gI().sendInfoHpMpMoney(pl);
                            Service.gI().sendThongBao(player, "Tặng ngọc thành công");
                            Service.gI().sendThongBao(pl,
                                    "Bạn được " + player.name + " tặng " + numruby + " ngọc xanh");
                            InventoryService.gI().subQuantityItemsBag(player, item, 1);
                            InventoryService.gI().sendItemBags(player);
                        } else {
                            Service.gI().sendThongBao(player, "Không đủ ngọc xanh để tặng");
                        }
                    } else {
                        Service.gI().sendThongBao(player, "Người chơi không tồn tại hoặc đang offline");
                    }
                }
                case BANGHOI -> {
                    Clan clan = player.clan;
                    if (clan != null) {
                        ClanMember cm = clan.getClanMember((int) player.id);
                        if (clan.isLeader(player)) {
                            if (clan.canUpdateClan(player)) {
                                String tenvt = text[0];
                                if (!Util.haveSpecialCharacter(tenvt) && tenvt.length() > 1 && tenvt.length() < 5) {
                                    clan.name2 = tenvt;
                                    clan.update();
                                    Service.gI().sendThongBao(player, "[" + tenvt + "] OK");
                                } else {
                                    Service.gI().sendThongBaoOK(player, "Chỉ chấp nhận các ký tự a-z, 0-9 và chiều dài từ 2 đến 4 ký tự");
                                }
                            }
                        }
                    }
                }
                case DISSOLUTION_CLAN -> {
                    String xacNhan = text[0];
                    Clan clan;
                    if (xacNhan.equalsIgnoreCase("OK")) {
                        clan = player.clan;
                        if (clan.isLeader(player)) {
                            clan.deleteDB(clan.id);
                            Manager.CLANS.remove(clan);
                            player.clan = null;
                            player.clanMember = null;
                            ClanService.gI().sendMyClan(player);
                            ClanService.gI().sendClanId(player);
                            Service.gI().sendThongBao(player, "Bang hội đã giải tán thành công.");
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    public void createForm(Player pl, int typeInput, String title, SubInput... subInputs) {
        pl.idMark.setTypeInput(typeInput);
        Message msg = null;
        try {
            msg = new Message(-125);
            msg.writer().writeUTF(title);
            msg.writer().writeByte(subInputs.length);
            for (SubInput si : subInputs) {
                msg.writer().writeUTF(si.name);
                msg.writer().writeByte(si.typeInput);
            }
            pl.sendMessage(msg);
        } catch (Exception e) {
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void createForm(ISession session, int typeInput, String title, SubInput... subInputs) {
        Message msg = null;
        try {
            msg = new Message(-125);
            msg.writer().writeUTF(title);
            msg.writer().writeByte(subInputs.length);
            for (SubInput si : subInputs) {
                msg.writer().writeUTF(si.name);
                msg.writer().writeByte(si.typeInput);
            }
            session.sendMessage(msg);
        } catch (Exception e) {
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void createFormChangePassword(Player pl) {
        createForm(pl, CHANGE_PASSWORD, "Đổi mật khẩu", new SubInput("Mật khẩu cũ", PASSWORD),
                new SubInput("Mật khẩu mới", PASSWORD),
                new SubInput("Nhập lại mật khẩu mới", PASSWORD));
    }

    public void createFormGiveItem(Player pl) {
        createForm(pl, GIVE_IT, "Tặng vật phẩm", new SubInput("Tên", ANY), new SubInput("Id Item", ANY), new SubInput("ID OPTION", ANY), new SubInput("PARAM", ANY), new SubInput("Số lượng", ANY));
    }

    public void createFormGetItem(Player pl) {
        createForm(pl, GET_IT, "Get vật phẩm", new SubInput("Id Item", ANY), new SubInput("ID OPTION", ANY), new SubInput("PARAM", ANY), new SubInput("Số lượng", ANY));
    }

    public void createFormGiftCode(Player pl) {
        createForm(pl, GIFT_CODE, "Giftcode", new SubInput("Gift-code", ANY));
    }

    public void createFormMBV(Player pl) {
        createForm(pl, MBV, "Đồ ngu! Đồ ăn hại! Cút mẹ mày đi!", new SubInput("Nhập Mã Bảo Vệ Đã Quên", NUMERIC), new SubInput("Nhập Mã Bảo Vệ Mới", NUMERIC), new SubInput("Nhập Lại Mã Bảo Vệ Mới", NUMERIC));
    }

    public void createFormBangHoi(Player pl) {
        createForm(pl, BANGHOI, "Nhập tên viết tắt bang hội", new SubInput("Tên viết tắt từ 2 đến 4 kí tự", ANY));
    }

    public void createFormFindPlayer(Player pl) {
        createForm(pl, FIND_PLAYER, "Tìm kiếm người chơi", new SubInput("Tên người chơi", ANY));
    }

    public void createFormFindPlayer1(Player pl) {
        Input.gI().createForm(pl, FIND_PLAYER_NAME, "Nhập tên người chơi bạn muốn tặng ngọc", new SubInput("Tên người chơi", ANY));

    }

    public void createFormNapThe(Player pl, byte loaiThe) {
        pl.idMark.setLoaiThe(loaiThe);
        createForm(pl, NAP_THE, "Nạp thẻ", new SubInput("Mã thẻ", ANY), new SubInput("Seri", ANY));
    }

    public void createFormTangRuby(Player pl) {
        createForm(pl, TANG_NGOC_HONG, "Tặng ngọc", new SubInput("Tên nhân vật", ANY),
                new SubInput("Số Hồng Ngọc Muốn Tặng", NUMERIC));
    }

    public void createFormTradeGold(Player pl) {
        createForm(pl, TRADE_GOLD, "Tỉ lệ quy đổi: 10.000 vnđ = 40 Thỏi vàng \n Số dư hiện tại: " + pl.getSession().vnd, new SubInput("Số lượng", NUMERIC));
    }

    public void createFormTradeGem(Player pl) {
        createForm(pl, TRADE_GEM, "Tỉ lệ quy đổi: 10.000 vnđ = 10000 ngọc \n Số dư hiện tại: " + pl.getSession().vnd, new SubInput("Số lượng", NUMERIC));
    }

    public void createFormChangeName(Player pl, Player plChanged) {
        PLAYER_ID_OBJECT.put((int) pl.id, plChanged);
        createForm(pl, CHANGE_NAME, "Đổi tên " + plChanged.name, new SubInput("Tên mới", ANY));
    }

    public void createFormChangeNameByItem(Player pl) {
        createForm(pl, CHANGE_NAME_BY_ITEM, "Đổi tên " + pl.name, new SubInput("Tên mới", ANY));
    }

    public void createFormChooseLevelBDKB(Player pl) {
        createForm(pl, CHOOSE_LEVEL_BDKB, "Hãy chọn cấp độ hang kho báu từ 1-110", new SubInput("Cấp độ", NUMERIC));
    }

    public void createFormChooseLevelCDRD(Player pl) {
        createForm(pl, CHOOSE_LEVEL_CDRD, "Hãy chọn cấp độ từ 1-110", new SubInput("Cấp độ", NUMERIC));
    }

    public void createFormChooseLevelKGHD(Player pl) {
        createForm(pl, CHOOSE_LEVEL_KGHD, "Hãy chọn cấp độ từ 1-110", new SubInput("Cấp độ", NUMERIC));
    }

    public void createFormSenditem1(Player pl) {
        createForm(pl, SEND_ITEM_OP, "SEND Vật Phẩm Option",
                new SubInput("Tên người chơi", ANY),
                new SubInput("ID Trang Bị", NUMERIC),
                new SubInput("ID Option", NUMERIC),
                new SubInput("Param", NUMERIC),
                new SubInput("Số lượng", NUMERIC));
    }

    public void createFormBanSLL(Player pl) {
        createForm(pl, BANSLL, "Bạn muốn bán bao nhiêu [Thỏi vàng] ?", new SubInput("Số lượng", NUMERIC));
    }

    public void createFormGiaiTanBangHoi(Player pl) {
        createForm(pl, DISSOLUTION_CLAN, "Nhập OK để xác nhận giải tán bang hội.", new SubInput("", ANY));
    }

    public void createFormConSoMayMan_Gold(Player pl) {
        createForm(pl, CON_SO_MAY_MAN_VANG, "Hãy chọn 1 số từ 0 đến 99 giá 1.000.000 vàng", new SubInput("Số bạn chọn", NUMERIC));
    }

    public void createFormConSoMayMan_Gem(Player pl) {
        createForm(pl, CON_SO_MAY_MAN_NGOC, "Hãy chọn 1 số từ 0 đến 99 giá 5 ngọc", new SubInput("Số bạn chọn", NUMERIC));
    }

    public void createFormTrongDua(Player player) {
        createForm(player, TRONG_DUA, "Trồng Dưa Hấu", new SubInput("Cần có hạt dưa để trồng", NUMERIC));
    }

    public void createFormBotQuai(Player pl) {
        createForm(pl, BOTQUAI, "Buff Bot Quái",
                new SubInput("số lượng bot", NUMERIC));
    }

    public void createFormBotBoss(Player pl) {
        createForm(pl, BOTBOSS, "Buff Bot Boss",
                new SubInput("số lượng bot", NUMERIC));
    }

    public void createFormBotItem(Player pl) {
        createForm(pl, BOTITEM, "Buff Bot Item",
                new SubInput("số lượng bot", NUMERIC),
                new SubInput("id item cần bán", NUMERIC),
                new SubInput("id item trao đổi", NUMERIC),
                new SubInput("số lượng yêu cầu trao đổi", NUMERIC));
    }

    public void createFormBotAttackPlayer(Player pl) {
        createForm(pl, BOTATTACKPLAYER, "Buff Bot Tấn Công Người",
                new SubInput("số lượng bot", NUMERIC));
    }

    public static class SubInput {

        private String name;
        private byte typeInput;

        public SubInput(String name, byte typeInput) {
            this.name = name;
            this.typeInput = typeInput;
        }
    }

}
