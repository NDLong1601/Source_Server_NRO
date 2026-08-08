package models;

import nro.models.managers.ShenronEventManager;
import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import lombok.Getter;
import lombok.Setter;
import nro.models.map.service.NpcService;
import nro.models.map.Zone;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.server.Client;
import nro.models.services.ItemTimeService;
import nro.models.services.Service;
import nro.models.utils.SkillUtil;
import nro.models.utils.Util;

public class ShenronEvent {

    @Setter
    @Getter
    private Player player;

    @Setter
    @Getter
    private Zone zone;

    public long playerId;
    public boolean isPlayerDisconnect;
    public byte select;
    public int shenronType;
    public boolean leaveMap;

    public static final byte WISHED = 0;
    public static final byte TIME_UP = 1;

    public static final byte DRAGON_EVENT = 1;

    public long lastTimeShenronWait;
    public static int timeResummonShenron = 300000;
    public static int timeShenronWait = 300000;

    public static final String SHENRON_SAY
            = "Ta sẽ ban cho người 1 điều ước, ngươi có 5 phút, hãy chọn đi:\n1) Đổi skill 3, 4 đệ tử (có thể trùng skill trước đó).\n2) Skill 5 đệ tử tăng thêm 1 cấp.\n3) Tăng 10% HP, KI, SD trong 30p.\n4) Quần đang mang tăng thêm 1 cấp.";

    public static final String[] SHENRON_WISHES
            = new String[]{"Điều ước 1", "Điều ước 2", "Điều ước 3", "Điều ước 4"};

    public boolean shenronLeave;

    public void update() {
        try {
            if (!shenronLeave) {
                if (isPlayerDisconnect) {
                    Player pl = Client.gI().getPlayer(playerId);
                    if (pl != null) {
                        player = pl;
                        if (player.zone != null && player.zone.map.mapId != 0 && player.zone.map.mapId != 7 && player.zone.map.mapId != 14
                                && player.zone.map.mapId != 21 && player.zone.map.mapId != 22 && player.zone.map.mapId != 23) {
                            //          player.shenronEvent = this;
                            zone = player.zone;
                            player.idMark.setShenronType(shenronType);
                            isPlayerDisconnect = false;
                            reSummonShenron();
                        }
                    }
                }
                if (Util.canDoWithTime(lastTimeShenronWait, timeShenronWait)) {
                    leaveMap = true;
                    NpcService.gI().createMenuRongThieng(player, ConstNpc.IGNORE_MENU, "Còn cái nịt =))\nCó không ước mất đừng tìm.", "Xin vĩnh biệt cụ........");
                    shenronLeave();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void reSummonShenron() {
        activeShenron(true, DRAGON_EVENT);
        sendWhishesShenron();
    }

    public void sendWhishesShenron() {
        NpcService.gI().createMenuRongThieng(player, ConstNpc.SHOW_SHENRON_EVENT_CONFIRM, SHENRON_SAY, SHENRON_WISHES);
    }

    public void showConfirmShenron(byte select) {
        this.select = select;
        String wish = null;
        switch (player.idMark.getShenronType()) {
            case 0:
                wish = SHENRON_WISHES[select];
                break;
        }
        NpcService.gI().createMenuRongThieng(player, ConstNpc.SHENRON_EVENT_CONFIRM, "Ngươi có chắc muốn ước?", wish, "Từ chối");
    }

    public void activeShenron(boolean appear, byte type) {
        Message msg;
        try {
            msg = new Message(-83);
            msg.writer().writeByte(appear ? 0 : (byte) 1);
            if (appear) {
                msg.writer().writeShort(player.zone.map.mapId);
                msg.writer().writeShort(player.zone.map.bgId);
                msg.writer().writeByte(player.zone.zoneId);
                msg.writer().writeInt((int) player.id);
                msg.writer().writeUTF("DucVuPro");
                msg.writer().writeShort(player.location.x);
                msg.writer().writeShort(player.location.y);
                msg.writer().writeByte(type);
                playerId = player.id;
                shenronType = player.idMark.getShenronType();
                zone.shenronType = shenronType;
                lastTimeShenronWait = System.currentTimeMillis();
                player.isShenronAppear = true;
            }
            Service.gI().sendMessAllPlayerInMap(player, msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void confirmWish() {
        switch (player.idMark.getShenronType()) {
            case 0:
                switch (this.select) {
                    case 0: //thay chiêu 3-4 đệ tử
                        if (player.pet != null) {
                            if (player.pet.playerSkill.skills.get(2).skillId != -1) {
                                player.pet.openSkill3();
                                if (player.pet.playerSkill.skills.get(3).skillId != -1) {
                                    player.pet.openSkill4();
                                }
                            } else {
                                Service.gI().sendThongBao(player, "Ít nhất đệ tử ngươi phải có chiêu 3 chứ!");
                                sendWhishesShenron();
                                return;
                            }
                        } else {
                            Service.gI().sendThongBao(player, "Ngươi làm gì có đệ tử?");
                            sendWhishesShenron();
                            return;
                        }
                        break;
                    case 1:
                        if (player.pet != null) {
                            if (SkillUtil.upSkillPet(player.pet.playerSkill.skills, 4)) {
                                Service.gI().chatJustForMe(player, player.pet, "Cám ơn sư phụ");
                            } else {
                                Service.gI().sendThongBao(player, "Skill đã đạt cấp tối đa hoặc đệ ngươi chưa có skill 5.");
                                sendWhishesShenron();
                                return;
                            }
                        } else {
                            Service.gI().sendThongBao(player, "Ngươi làm gì có đệ tử?");
                            sendWhishesShenron();
                            return;
                        }
                        break;
                    case 2:// Tăng hp, ki, sd
                        int timeRX = player.itemTime.timeRX / 1000 + 1800;
                        int maxTimeInSeconds = 32767;
                        if (timeRX >= maxTimeInSeconds) {
                            Service.gI().sendThongBao(player, "Ước ít thôi con :v");
                            sendWhishesShenron();
                            return;
                        }
                        player.itemTime.isUseRX = true;
                        player.itemTime.timeRX = timeRX * 1000;
                        player.itemTime.lastTimeUseRX = System.currentTimeMillis();
                        ItemTimeService.gI().sendItemTime(player, 6581, timeRX);
                        player.nPoint.calPoint();
                        player.nPoint.setHp((int) player.nPoint.hpMax);
                        player.nPoint.setMp((int) player.nPoint.mpMax);
                        Service.gI().point(player);
                        Service.gI().Send_Info_NV(player);
                        break;
                    case 3: //quần đang đeo lên 1 cấp
                        Item item = this.player.inventory.itemsBody.get(1);
                        if (item.isNotNullItem()) {
                            int level = 0;
                            for (Item.ItemOption io : item.itemOptions) {
                                if (io.optionTemplate.id == 72) {
                                    level = io.param;
                                    if (level < 7) {
                                        io.param++;
                                    }
                                    break;
                                }
                            }
                            if (level < 7) {
                                if (level == 0) {
                                    item.itemOptions.add(new Item.ItemOption(72, 1));
                                }
                                for (Item.ItemOption io : item.itemOptions) {
                                    if (InventoryService.gI().optionCanUpgrade(io.optionTemplate.id)) {
                                        io.param += (io.param * 10 / 100);
                                    }
                                }
                                InventoryService.gI().sendItemBody(player);
                            } else {
                                Service.gI().sendThongBao(player, "Quần của ngươi đã đạt cấp tối đa");
                                sendWhishesShenron();
                                return;
                            }
                        } else {
                            Service.gI().sendThongBao(player, "Ngươi hiện tại có mang quần đâu");
                            sendWhishesShenron();
                            return;
                        }
                }
                break;
        }
        shenronLeave();
    }

    public void shenronLeave() {
        if (!shenronLeave) {
            shenronLeave = true;
            if (player != null && player.zone != null) {
                player.shenronEvent = null;
                if (!leaveMap) {
                    NpcService.gI().createTutorial(player, 0, "Điều ước của ngươi đã được thực hiện...tạm biệt");
                }
                activeShenron(false, DRAGON_EVENT);
                player.isShenronAppear = false;
                select = -1;
            }
            zone.shenronType = -1;
            player.lastTimeShenronAppeared = System.currentTimeMillis();
            ShenronEventManager.gI().remove(this);
        }
    }
}
