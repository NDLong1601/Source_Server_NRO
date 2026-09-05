package nro.models.player;

import nro.models.consts.ConstPlayer;
import nro.models.consts.ConstTask;
import nro.models.data.DataGame;
import nro.models.item.Item;
import nro.models.minigame.ChonAiDay_Gem;
import nro.models.minigame.ChonAiDay_Gold;
import nro.models.radar.Card;
import nro.models.radar.RadarCard;
import nro.models.server.Manager;
import nro.models.services.InventoryService;
import nro.models.services.RadarService;
import nro.models.services.TaskService;

public final class PlayerAppearanceService {

    private PlayerAppearanceService() {
    }

    public static String percentGold(Player player, int type) {
        try {
            if (type == 0) {
                double denominator = ChonAiDay_Gold.gI().goldNormar;
                if (denominator != 0) {
                    double percent = ((double) player.goldNormar / denominator) * 100;
                    return String.valueOf(Math.ceil(percent));
                } else {
                    return "0";
                }
            } else if (type == 1) {
                double denominator = ChonAiDay_Gold.gI().goldVip;
                if (denominator != 0) {
                    double percent = ((double) player.goldVIP / denominator) * 100;
                    return String.valueOf(Math.ceil(percent));
                } else {
                    return "0";
                }
            }
        } catch (ArithmeticException e) {
            return "0";
        }
        return "0";
    }

    public static String percentGem(Player player, int type) {
        try {
            if (type == 0) {
                double denominator3 = ChonAiDay_Gem.gI().gemNormar;
                if (denominator3 != 0) {
                    double percent = ((double) player.gemNormar / denominator3) * 100;
                    return String.valueOf(Math.ceil(percent));
                } else {
                    return "0";
                }
            } else if (type == 1) {
                double denominator3 = ChonAiDay_Gem.gI().gemVip;
                if (denominator3 != 0) {
                    double percent = ((double) player.gemVIP / denominator3) * 100;
                    return String.valueOf(Math.ceil(percent));
                } else {
                    return "0";
                }
            } else {
                return "0";
            }
        } catch (ArithmeticException | NullPointerException e) {
            return "0";
        }
    }

    public static int getHat(Player player) {
        return -1;
    }

    public static byte getAura(Player player) {
        byte auraFromItem = getAuraFromEquippedItem(player);
        if (auraFromItem >= 0) {
            return auraFromItem;
        }
        if (!player.isPl() || player.Cards.isEmpty()) {
            return -1;
        }
        for (Card card : player.Cards) {
            if (card != null && (card.Id == 956 || card.Id == 1792 || card.Id == 1793 || card.Id == 1791 || card.Id == 1204 || card.Id == 1142) && card.Level > 1) {
                RadarCard radarTemplate = RadarService.gI().RADAR_TEMPLATE.stream().filter(r -> r.Id == card.Id).findFirst().orElse(null);
                if (radarTemplate != null) {
                    return (byte) radarTemplate.AuraId;
                }
            }
        }
        return -1;
    }

    static byte getAuraFromEquippedItem(Player player) {
        if (!player.isPl() || player.inventory == null || player.inventory.itemsBody == null
                || player.inventory.itemsBody.size() <= InventoryService.PLAYER_AURA_SLOT) {
            return -1;
        }
        Item auraItem = player.inventory.itemsBody.get(InventoryService.PLAYER_AURA_SLOT);
        int auraId = InventoryService.getAuraId(auraItem);
        if (auraId < 0 || auraId > Byte.MAX_VALUE) {
            return -1;
        }
        if (Manager.getNFrameImageByName("aura_" + auraId + "_0") <= 0
                || Manager.getNFrameImageByName("aura_" + auraId + "_1") <= 0) {
            return -1;
        }
        return (byte) auraId;
    }

    public static byte getEffFront(Player player) {
        if (player.inventory == null) {
            return -1;
        }
        if (player.inventory.itemsBody.isEmpty() || player.inventory.itemsBody.size() < 10) {
            return -1;
        }
        int levelAo = 0;
        Item.ItemOption optionLevelAo = null;
        int levelQuan = 0;
        Item.ItemOption optionLevelQuan = null;
        int levelGang = 0;
        Item.ItemOption optionLevelGang = null;
        int levelGiay = 0;
        Item.ItemOption optionLevelGiay = null;
        int levelNhan = 0;
        Item.ItemOption optionLevelNhan = null;
        Item itemAo = player.inventory.itemsBody.get(0);
        Item itemQuan = player.inventory.itemsBody.get(1);
        Item itemGang = player.inventory.itemsBody.get(2);
        Item itemGiay = player.inventory.itemsBody.get(3);
        Item itemNhan = player.inventory.itemsBody.get(4);
        for (Item.ItemOption io : itemAo.itemOptions) {
            if (io.optionTemplate.id == 72) {
                levelAo = io.param;
                optionLevelAo = io;
                break;
            }
        }
        for (Item.ItemOption io : itemQuan.itemOptions) {
            if (io.optionTemplate.id == 72) {
                levelQuan = io.param;
                optionLevelQuan = io;
                break;
            }
        }
        for (Item.ItemOption io : itemGang.itemOptions) {
            if (io.optionTemplate.id == 72) {
                levelGang = io.param;
                optionLevelGang = io;
                break;
            }
        }
        for (Item.ItemOption io : itemGiay.itemOptions) {
            if (io.optionTemplate.id == 72) {
                levelGiay = io.param;
                optionLevelGiay = io;
                break;
            }
        }
        for (Item.ItemOption io : itemNhan.itemOptions) {
            if (io.optionTemplate.id == 72) {
                levelNhan = io.param;
                optionLevelNhan = io;
                break;
            }
        }
        if (optionLevelAo != null && optionLevelQuan != null && optionLevelGang != null && optionLevelGiay != null && optionLevelNhan != null
                && levelAo >= 8 && levelQuan >= 8 && levelGang >= 8 && levelGiay >= 8 && levelNhan >= 8) {
            return 8;
        } else if (optionLevelAo != null && optionLevelQuan != null && optionLevelGang != null && optionLevelGiay != null && optionLevelNhan != null
                && levelAo >= 7 && levelQuan >= 7 && levelGang >= 7 && levelGiay >= 7 && levelNhan >= 7) {
            return 7;
        } else if (optionLevelAo != null && optionLevelQuan != null && optionLevelGang != null && optionLevelGiay != null && optionLevelNhan != null
                && levelAo >= 6 && levelQuan >= 6 && levelGang >= 6 && levelGiay >= 6 && levelNhan >= 6) {
            return 6;
        } else if (optionLevelAo != null && optionLevelQuan != null && optionLevelGang != null && optionLevelGiay != null && optionLevelNhan != null
                && levelAo >= 5 && levelQuan >= 5 && levelGang >= 5 && levelGiay >= 5 && levelNhan >= 5) {
            return 5;
        } else if (optionLevelAo != null && optionLevelQuan != null && optionLevelGang != null && optionLevelGiay != null && optionLevelNhan != null
                && levelAo >= 4 && levelQuan >= 4 && levelGang >= 4 && levelGiay >= 4 && levelNhan >= 4) {
            return 4;
        } else {
            return -1;
        }
    }

    public static short getHead(Player player) {
        if (player.isPl() && player.pet != null && player.fusion.typeFusion == ConstPlayer.HOP_THE_GOGETA || player.fusion.typeFusion == ConstPlayer.LUONG_LONG_NHAT_THE || player.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA || player.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2 || player.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA3) {
            Item item = player.inventory.itemsBody.get(5);
            Item petItem = player.pet.inventory.itemsBody.get(5);
            boolean hasItem1 = item.isNotNullItem() && (item.template.id == 1693 || item.template.id == 1553);
            boolean hasItem2 = petItem.isNotNullItem() && (petItem.template.id == 1693 || petItem.template.id == 1553);
            boolean sameItem = item.isNotNullItem() && petItem.isNotNullItem() && item.template.id == petItem.template.id;
            if (hasItem1 && hasItem2 && !sameItem) {
                return 1578;
            }
        }
        if (player.effectSkill != null && player.effectSkill.isBinh) {
            return Player.idOutfitMafuba[player.effectSkill.typeBinh][0];
        }
        if (player.effectSkill != null && player.effectSkill.isStone) {
            return 454;
        }
        if (player.effectSkill != null && player.effectSkill.isHalloween) {
            return Player.idOutfitHalloween[player.effectSkill.idOutfitHalloween][player.gender][0];
        }
        if (player.effectSkill != null && player.effectSkill.isMonkey) {
            return (short) ConstPlayer.HEADMONKEY[player.effectSkill.levelMonkey - 1];
        } else if (player.effectSkill != null && player.effectSkill.isSocola) {
            return 412;
        } else if (player.effectSkill != null && player.effectSkill.isCarrot) {
            return 669;
        } else if (player.effectSkill != null && player.effectSkill.isPumpkin) {
            return 584;
        } else if (player.fusion != null && player.fusion.typeFusion != ConstPlayer.NON_FUSION) {
            if (player.nPoint != null && player.nPoint.isGogeta) {
                return 2100;
            } else if (player.fusion.typeFusion == ConstPlayer.LUONG_LONG_NHAT_THE) {
                return Player.idOutfitFusion[player.gender == ConstPlayer.NAMEC ? 2 : 0][0];
            } else if (player.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA) {
//                if (this.pet.typePet == 1) {
//                    return idOutfitFusion[3 + this.gender][0];
//                }
                return Player.idOutfitFusion[player.gender == ConstPlayer.NAMEC ? 2 : 1][0];
            } else if (player.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2) {
                if (player.nPoint != null && player.nPoint.levelBT == 3) {
                    return Player.idOutfitFusion[3 + player.gender][0];
                }
                return Player.idOutfitFusion[3 + player.gender][0];
            } else if (player.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA3) {
                if (player.nPoint != null && player.nPoint.levelBT == 4) {
                    return Player.idOutfitFusion[6 + player.gender][0];
                }
                return Player.idOutfitFusion[6 + player.gender][0];
            }
        } else if (player.inventory != null && player.inventory.itemsBody.get(5).isNotNullItem()) {
            int headId = player.inventory.itemsBody.get(5).template.head;
            if (headId != -1) {
                return (short) headId;
            }
        }
        return player.head;
    }

    public static short getBody(Player player) {
        if (player.isPl() && player.pet != null && player.fusion.typeFusion == ConstPlayer.HOP_THE_GOGETA || player.fusion.typeFusion == ConstPlayer.LUONG_LONG_NHAT_THE || player.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA || player.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2 || player.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA3) {
            Item item = player.inventory.itemsBody.get(5);
            Item petItem = player.pet.inventory.itemsBody.get(5);

            boolean hasItem1 = item.isNotNullItem() && (item.template.id == 1693 || item.template.id == 1553);
            boolean hasItem2 = petItem.isNotNullItem() && (petItem.template.id == 1693 || petItem.template.id == 1553);
            boolean sameItem = item.isNotNullItem() && petItem.isNotNullItem() && item.template.id == petItem.template.id;
            if (hasItem1 && hasItem2 && !sameItem) {
                return 1581;
            }
        }
        if (player.effectSkill != null && player.effectSkill.isBinh) {
            return Player.idOutfitMafuba[player.effectSkill.typeBinh][1];
        }
        if (player.effectSkill != null && player.effectSkill.isStone) {
            return 455;
        }
        if (player.effectSkill != null && player.effectSkill.isHalloween) {
            return Player.idOutfitHalloween[player.effectSkill.idOutfitHalloween][player.gender][1];
        }
        if (player.effectSkill != null && player.effectSkill.isMonkey) {
            return 193;
        } else if (player.effectSkill != null && player.effectSkill.isSocola) {
            return 413;
        } else if (player.effectSkill != null && player.effectSkill.isCarrot) {
            return 670;
        } else if (player.effectSkill != null && player.effectSkill.isPumpkin) {
            return 585;
        } else if (player.isPhuHoMapMabu && player.fusion != null && player.fusion.typeFusion == ConstPlayer.NON_FUSION) {
            return Player.idOutfitGod[player.gender][1];
        } else if (player.fusion != null && player.fusion.typeFusion != ConstPlayer.NON_FUSION) {
            if (player.nPoint != null && player.nPoint.isGogeta) {
                return 2101;
            } else if (player.fusion.typeFusion == ConstPlayer.LUONG_LONG_NHAT_THE) {
                return Player.idOutfitFusion[player.gender == ConstPlayer.NAMEC ? 2 : 0][1];
            } else if (player.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA) {
//                if (this.pet.typePet == 1) {
//                    return idOutfitFusion[3 + this.gender][1];
//                }
                return Player.idOutfitFusion[player.gender == ConstPlayer.NAMEC ? 2 : 1][1];
            } else if (player.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2) {
                if (player.nPoint != null && player.nPoint.levelBT == 3) {
                    return Player.idOutfitFusion[3 + player.gender][1];
                }
                return Player.idOutfitFusion[3 + player.gender][1];
            } else if (player.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA3) {
                if (player.nPoint != null && player.nPoint.levelBT == 4) {
                    return Player.idOutfitFusion[6 + player.gender][1];
                }
                return Player.idOutfitFusion[6 + player.gender][1];
            }
        } else if (player.inventory != null && player.inventory.itemsBody.get(5).isNotNullItem()) {
            int body = player.inventory.itemsBody.get(5).template.body;
            if (body != -1) {
                return (short) body;
            }
        }
        if (player.inventory != null && player.inventory.itemsBody.get(0).isNotNullItem()) {
            return player.inventory.itemsBody.get(0).template.part;
        }
        return (short) (player.gender == ConstPlayer.NAMEC ? 59 : 57);
    }

    public static short getLeg(Player player) {
        if (player.isPl() && player.pet != null && player.fusion.typeFusion == ConstPlayer.HOP_THE_GOGETA || player.fusion.typeFusion == ConstPlayer.LUONG_LONG_NHAT_THE || player.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA || player.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2 || player.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA3) {
            Item item = player.inventory.itemsBody.get(5);
            Item petItem = player.pet.inventory.itemsBody.get(5);

            boolean hasItem1 = item.isNotNullItem() && (item.template.id == 1693 || item.template.id == 1553);
            boolean hasItem2 = petItem.isNotNullItem() && (petItem.template.id == 1693 || petItem.template.id == 1553);
            boolean sameItem = item.isNotNullItem() && petItem.isNotNullItem() && item.template.id == petItem.template.id;
            if (hasItem1 && hasItem2 && !sameItem) {
                return 1582;
            }
        }
        if (player.effectSkill != null && player.effectSkill.isBinh) {
            return Player.idOutfitMafuba[player.effectSkill.typeBinh][2];
        }
        if (player.effectSkill != null && player.effectSkill.isStone) {
            return 456;
        }
        if (player.effectSkill != null && player.effectSkill.isHalloween) {
            return Player.idOutfitHalloween[player.effectSkill.idOutfitHalloween][player.gender][2];
        }
        if (player.effectSkill != null && player.effectSkill.isMonkey) {
            return 194;
        } else if (player.effectSkill != null && player.effectSkill.isSocola) {
            return 414;
        } else if (player.effectSkill != null && player.effectSkill.isCarrot) {
            return 671;
        } else if (player.effectSkill != null && player.effectSkill.isPumpkin) {
            return 586;
        } else if (player.isPhuHoMapMabu && player.fusion != null && player.fusion.typeFusion == ConstPlayer.NON_FUSION) {
            return Player.idOutfitGod[player.gender][2];
        } else if (player.fusion != null && player.fusion.typeFusion != ConstPlayer.NON_FUSION) {
            if (player.nPoint != null && player.nPoint.isGogeta) {
                return 2102;
            } else if (player.fusion.typeFusion == ConstPlayer.LUONG_LONG_NHAT_THE) {
                return Player.idOutfitFusion[player.gender == ConstPlayer.NAMEC ? 2 : 0][2];
            } else if (player.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA) {
//                if (this.pet.typePet == 1) {
//                    return idOutfitFusion[3 + this.gender][2];
//                }
                return Player.idOutfitFusion[player.gender == ConstPlayer.NAMEC ? 2 : 1][2];
            } else if (player.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2) {
                if (player.nPoint != null && player.nPoint.levelBT == 3) {
                    return Player.idOutfitFusion[3 + player.gender][2];
                }
                return Player.idOutfitFusion[3 + player.gender][2];
            } else if (player.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA3) {
                if (player.nPoint != null && player.nPoint.levelBT == 4) {
                    return Player.idOutfitFusion[6 + player.gender][2];
                }
                return Player.idOutfitFusion[6 + player.gender][2];
            }
        } else if (player.inventory != null && player.inventory.itemsBody.get(5).isNotNullItem()) {
            int leg = player.inventory.itemsBody.get(5).template.leg;
            if (leg != -1) {
                return (short) leg;
            }
        }
        if (player.inventory != null && player.inventory.itemsBody.get(1).isNotNullItem()) {
            return player.inventory.itemsBody.get(1).template.part;
        }
        return (short) (player.gender == 1 ? 60 : 58);
    }

    public static short getFlagBag(Player player) {
        if (player.idMark.isHoldBlackBall()) {
            return 31;
        } else if (player.idNRNM >= 353 && player.idNRNM <= 359) {
            return 30;
        }
        if (TaskService.gI().getIdTask(player) == ConstTask.TASK_3_2) {
            return 28;
        }
        if (player.inventory.itemsBody.size() >= 11) {
            if (player.inventory.itemsBody.get(8).isNotNullItem()) {
                return player.inventory.itemsBody.get(8).template.part;
            }
        }
        if (player.isPet && player.inventory.itemsBody.size() >= 8) {
            if (player.inventory.itemsBody.get(7).isNotNullItem()) {
                return player.inventory.itemsBody.get(7).template.part;
            }
        }
        if (player.clan != null) {
            return (short) player.clan.imgId;
        }
        return -1;
    }

    public static short getMount(Player player) {
        if (player.inventory.itemsBody.isEmpty() || player.inventory.itemsBody.size() < 10) {
            return -1;
        }
        Item item = player.inventory.itemsBody.get(9);
        if (!item.isNotNullItem()) {
            return -1;
        }
        if (item.template.type == 24 || item.template.type == 23) {
            if (item.template.gender == 3 || item.template.gender == player.gender) {
                return item.template.id;
            } else {
                return -1;
            }
        } else {
            if (item.template.id < 500) {
                return item.template.id;
            } else {
                Short value = (Short) DataGame.MAP_MOUNT_NUM.get(item.template.id);
                return value != null ? value : -1;
            }
        }
    }
}
