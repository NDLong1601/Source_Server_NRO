package nro.models.Bot;

import java.util.Random;
import nro.models.player_system.Template;
import nro.models.player_system.Template.ItemTemplate;
import nro.models.server.Manager;
import nro.models.services.ItemService;

public class NewBot {

    public static NewBot i;

    public boolean LOAD_PART = true;
    public int MAXPART = 0;
    public static int[][] PARTBOT = new int[Manager.ITEM_TEMPLATES.size()][4];

    private final String[] FIRST_NAMES = java.util.stream.IntStream.rangeClosed(1, 50000)
            .mapToObj(i -> "mrblue" + i)
            .toArray(String[]::new);

    public static NewBot gI() {
        if (i == null) {
            i = new NewBot();
        }
        return i;
    }

    public void LoadPart() {
        if (LOAD_PART) {
            int i = 0;
            for (Template.ItemTemplate it : Manager.ITEM_TEMPLATES) {
                if (it.type == 5) {
                    if (it.head != -1 && it.leg != -1 && it.body != -1 && it.leg != 194) {
                        PARTBOT[i][0] = it.head;
                        PARTBOT[i][1] = it.leg;
                        PARTBOT[i][2] = it.body;
                        PARTBOT[i][3] = it.gender;
                        i++;
                        MAXPART++;
                    }
                }
            }
            LOAD_PART = false;
        }
    }

    public String Getname() {
        return FIRST_NAMES[new Random().nextInt(FIRST_NAMES.length)];
    }

    public int getIndex(int gender) {
        int Random = new Random().nextInt(MAXPART);
        int gend = PARTBOT[Random][3];
        if (gend == gender) {
            return Random;
        } else {
            return getIndex(gender);
        }
    }

    public void runBot(int type, BotGiaoDich shop, int slot) {
        LoadPart();
        for (int i = 0; i < slot; i++) {
            int Gender = new Random().nextInt(3);
            int Random1 = getIndex(Gender);
            int head = PARTBOT[Random1][0];
            int leg = PARTBOT[Random1][1];
            int body = PARTBOT[Random1][2];

            BotGiaoDich shopBotCopy = null;
            if (shop != null) {
                shopBotCopy = new BotGiaoDich(shop);
            }

            int flag = Manager.gI().FLAGS_BAGS.get(new Random().nextInt(Manager.gI().FLAGS_BAGS.size())).id;

            Bot b;
            if (type == 3) {
                b = new BotAttackplayer((short) head, (short) body, (short) leg, type, Getname(), (short) flag);
            } else {
                b = new Bot((short) head, (short) body, (short) leg, type, Getname(), shopBotCopy, (short) flag);
            }

            BotSanBoss bos = new BotSanBoss(b);
            BotPemQuai mo1 = new BotPemQuai(b);
            b.mo1 = mo1;
            b.boss = bos;

            int congThem = new Random().nextInt(50000000);
            b.nPoint.limitPower = 8;
            b.nPoint.power = 1000 + congThem;
            b.nPoint.tiemNang = 20000000 + congThem;
            b.nPoint.dameg = 600000;
            b.nPoint.mpg = 600000;
            b.nPoint.mpMax = 2000000000;
            b.nPoint.mp = 600000;
            b.nPoint.hpg = 675000;
            b.nPoint.hpMax = 10000;
            b.nPoint.hp = 600000;
            b.nPoint.maxStamina = 20000;
            b.nPoint.stamina = 20000;
            b.nPoint.critg = 10;
            b.nPoint.defg = 10;
            b.gender = (byte) Gender;
            b.charms.tdThuHut = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000);

            b.leakSkill();
            b.joinMap();

            for (int j = 0; j < 500; j++) {
                b.inventory.itemsBag.add(ItemService.gI().createItemNull());
            }

            if (shopBotCopy != null) {
                shopBotCopy.bot = b;
            }

            if (b != null) {
                BotManager.gI().bot.add(b);
            }
        }
    }

}
