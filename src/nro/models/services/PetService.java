package nro.models.services;

import nro.models.consts.ConstPlayer;
import nro.models.player.NewPet;
import nro.models.player.Pet;
import nro.models.player.PetConfig;
import nro.models.player.Player;
import nro.models.map.service.ChangeMapService;
import nro.models.utils.SkillUtil;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 *
 */
public class PetService {

    private static PetService instance;

    public static PetService gI() {
        if (instance == null) {
            instance = new PetService();
        }
        return instance;
    }

    public void createNormalPet(Player player, int gender, byte... limitPower) {
        new Thread(() -> {
            try {
                createNewPet(player, false, false, false, false, (byte) gender);
                if (limitPower != null && limitPower.length == 1) {
                    player.pet.nPoint.limitPower = limitPower[0];
                }
                Thread.sleep(1000);
                Service.gI().chatJustForMe(player, player.pet, "Xin hãy thu nhận tao làm đệ tử");
            } catch (Exception e) {
            }
        }).start();
    }

    public void createNormalPet(Player player, byte... limitPower) {
        new Thread(() -> {
            try {
                createNewPet(player, false, false, false, false);
                if (limitPower != null && limitPower.length == 1) {
                    player.pet.nPoint.limitPower = limitPower[0];
                }
                Thread.sleep(1000);
                Service.gI().chatJustForMe(player, player.pet, "Xin hãy thu nhận tao làm đệ tử");
            } catch (Exception e) {
            }
        }).start();
    }

    public void createMabuPet(Player player, int gender, byte... limitPower) {
        new Thread(() -> {
            try {
                createNewPet(player, true, false, false, false, (byte) gender);
                if (limitPower != null && limitPower.length == 1) {
                    player.pet.nPoint.limitPower = limitPower[0];
                }
                Thread.sleep(1000);
                Service.gI().chatJustForMe(player, player.pet, "Oa oa oa...");
            } catch (Exception e) {
            }
        }).start();
    }

    public void createMabuPet(Player player, byte... limitPower) {
        new Thread(() -> {
            try {
                createNewPet(player, true, false, false, false);
                if (limitPower != null && limitPower.length == 1) {
                    player.pet.nPoint.limitPower = limitPower[0];
                }
                Thread.sleep(1000);
                Service.gI().chatJustForMe(player, player.pet, "Oa oa oa...");
            } catch (Exception e) {
            }
        }).start();
    }

    public void createUubPet(Player player, byte... limitPower) {
        new Thread(() -> {
            try {
                createNewPet(player, false, true, false, false, (byte) player.gender);
                if (limitPower != null && limitPower.length == 1) {
                    player.pet.nPoint.limitPower = limitPower[0];
                }
                Thread.sleep(1000);
                Service.gI().chatJustForMe(player, player.pet, "Xin hãy thu nhận tao làm đệ tử");
            } catch (Exception e) {
            }
        }).start();
    }
    
    public void createJirenPet(Player player, byte... limitPower) {
        new Thread(() -> {
            try {
                createNewPet(player, false, false, false,true, (byte) player.gender);
                if (limitPower != null && limitPower.length == 1) {
                    player.pet.nPoint.limitPower = limitPower[0];
                }
                Thread.sleep(1000);
                Service.gI().chatJustForMe(player, player.pet, "Xin hãy thu nhận tao làm đệ tử");
            } catch (Exception e) {
            }
        }).start();
    }

    public void createKidBeerPet(Player player, byte... limitPower) {
        new Thread(() -> {
            try {
                createNewPet(player, false, false, true, false, (byte) player.gender);
                if (limitPower != null && limitPower.length == 1) {
                    player.pet.nPoint.limitPower = limitPower[0];
                }
                Thread.sleep(1000);
                Service.gI().chatJustForMe(player, player.pet, "Hãy hợp tác với ta, Kakarot!");
            } catch (Exception e) {
            }
        }).start();
    }

    public void changeNormalPet(Player player, int gender) {
        byte limitPower = player.pet.nPoint.limitPower;
        if (player.fusion.typeFusion != ConstPlayer.NON_FUSION) {
            player.pet.unFusion();
        }
        ChangeMapService.gI().exitMap(player.pet);
        player.pet.dispose();
        player.pet = null;
        createNormalPet(player, gender, limitPower);
    }

    public void changeNormalPet(Player player) {
        byte limitPower = player.pet.nPoint.limitPower;
        if (player.fusion.typeFusion != ConstPlayer.NON_FUSION) {
            player.pet.unFusion();
        }
        ChangeMapService.gI().exitMap(player.pet);
        player.pet.dispose();
        player.pet = null;
        createNormalPet(player, limitPower);
    }

    public void changeMabuPet(Player player) {
        byte limitPower = player.pet.nPoint.limitPower;
        if (player.fusion.typeFusion != ConstPlayer.NON_FUSION) {
            player.pet.unFusion();
        }
        ChangeMapService.gI().exitMap(player.pet);
        player.pet.dispose();
        player.pet = null;
        createMabuPet(player, limitPower);
    }

    public void changeUubPet(Player player) {
        byte limitPower = removeCurrentPet(player);
        createUubPet(player, limitPower);
    }

    public void changeKidBeerPet(Player player) {
        byte limitPower = removeCurrentPet(player);
        createKidBeerPet(player, limitPower);
    }
    
    public void changeJirenPet(Player player) {
        byte limitPower = removeCurrentPet(player);
        createJirenPet(player, limitPower);
    }

    private byte removeCurrentPet(Player player) {
        if (player.pet == null) {
            return (byte) PetConfig.getStartLimitPower();
        }
        byte limitPower = player.pet.nPoint.limitPower;
        if (player.fusion.typeFusion != ConstPlayer.NON_FUSION) {
            player.pet.unFusion();
        }
        ChangeMapService.gI().exitMap(player.pet);
        player.pet.dispose();
        player.pet = null;
        return limitPower;
    }

    public void changeMabuPet(Player player, int gender) {
        byte limitPower = player.pet.nPoint.limitPower;
        if (player.fusion.typeFusion != ConstPlayer.NON_FUSION) {
            player.pet.unFusion();
        }
        ChangeMapService.gI().exitMap(player.pet);
        player.pet.dispose();
        player.pet = null;
        createMabuPet(player, gender, limitPower);
    }

    public void changeNamePet(Player player, String name) {
        try {
            int renameItemId = PetConfig.getInt("pet.rename.itemId", 400, 0, Short.MAX_VALUE);
            int maxLength = PetConfig.getInt("pet.rename.maxLength", 10, 1, 100);
            if (!InventoryService.gI().isExistItemBag(player, renameItemId)) {
                Service.gI().sendThongBao(player, "Bạn cần thẻ đặt tên đệ tử, mua tại Santa");
                return;
            } else if (Util.haveSpecialCharacter(name)) {
                Service.gI().sendThongBao(player, "Tên không được chứa ký tự đặc biệt");
                return;
            } else if (name.length() > maxLength) {
                Service.gI().sendThongBao(player, "Tên quá dài");
                return;
            }
            ChangeMapService.gI().exitMap(player.pet);
            player.pet.name = "$" + name.toLowerCase().trim();
            InventoryService.gI().subQuantityItemsBag(player, InventoryService.gI().findItemBag(player, renameItemId), 1);
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    Service.gI().chatJustForMe(player, player.pet, "Cảm ơn sư phụ đã đặt cho con tên " + name);
                } catch (Exception e) {
                }
            }).start();
        } catch (Exception ex) {

        }
    }

    private int[] getDataPet(int typePet) {
        int[] petData = new int[5];
        String[] stats = {"hp", "mp", "damage", "defense", "critical"};
        for (int i = 0; i < stats.length; i++) {
            petData[i] = Util.nextInt(PetConfig.getStartStatMin(typePet, stats[i]),
                    PetConfig.getStartStatMax(typePet, stats[i]));
        }
        return petData;
    }

    private void createNewPet(Player player, boolean isMabu, boolean isUub, boolean isKidBeer, boolean isJiren, byte... gender) {
        int typePet = isMabu ? 1 : isUub ? 2 : isKidBeer ? 3 : isJiren ? 4 : 0;
        int[] data = getDataPet(typePet);

        Pet pet = new Pet(player);

        pet.name = "$" + (isMabu ? "Mabư" : isUub ? "Uub" : isKidBeer ? "Kid Beer" : isJiren ? "Kid Jiren" : "Đệ tử");

        pet.gender = (gender != null && gender.length != 0) ? gender[0] : (byte) Util.nextInt(0, 2);

        pet.id = player.isPl() ? -player.id : -Math.abs(player.id) - 100000;

        pet.nPoint.power = PetConfig.getStartPower(typePet);
        pet.nPoint.limitPower = (byte) PetConfig.getStartLimitPower();

        pet.typePet = (byte) typePet;
        pet.type = pet.typePet;

        pet.nPoint.stamina = (short) PetConfig.getStartStamina();
        pet.nPoint.maxStamina = pet.nPoint.stamina;
        pet.nPoint.hpg = data[0];
        pet.nPoint.mpg = data[1];
        pet.nPoint.dameg = data[2];
        pet.nPoint.defg = data[3];
        pet.nPoint.critg = data[4];

        int itemBodySize = 7;
        if (pet.typePet == 2 || pet.typePet == 3 || pet.typePet == 4) {
            itemBodySize = 9;
        }
        for (int i = 0; i < itemBodySize; i++) {
            pet.inventory.itemsBody.add(ItemService.gI().createItemNull());
        }

        pet.playerSkill.skills.add(SkillUtil.createSkill(Util.nextInt(0, 2) * 2, 1));
        for (int i = 0; i < 4; i++) {
            pet.playerSkill.skills.add(SkillUtil.createEmptySkill());
        }

        pet.nPoint.setFullHpMp();
        player.pet = pet;
    }

    public static void Pet2(Player pl, int h, int b, int l) {
        if (pl.newPet != null) {
            pl.newPet.dispose();
        }
        pl.newPet = new NewPet(pl, (short) h, (short) b, (short) l);
        pl.newPet.name = "$";
        pl.newPet.gender = pl.gender;
        pl.newPet.nPoint.tiemNang = 1;
        pl.newPet.nPoint.power = 1;
        pl.newPet.nPoint.limitPower = 1;
        pl.newPet.nPoint.hpg = 500000000;
        pl.newPet.nPoint.mpg = 500000000;
        pl.newPet.nPoint.hp = 500000000;
        pl.newPet.nPoint.mp = 500000000;
        pl.newPet.nPoint.dameg = 1;
        pl.newPet.nPoint.defg = 1;
        pl.newPet.nPoint.critg = 1;
        pl.newPet.nPoint.stamina = 1;
        pl.newPet.nPoint.setBasePoint();
        pl.newPet.nPoint.setFullHpMp();
    }
}
