package nro.models.data;

import nro.models.player_system.Template.HeadAvatar;
import nro.models.player_system.Template.MapTemplate;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

import nro.models.utils.FileIO;
import nro.models.services.Service;
import nro.models.skill.NClass;
import nro.models.skill.Skill;
import nro.models.player_system.Template.MobTemplate;
import nro.models.player_system.Template.NpcTemplate;
import nro.models.player_system.Template.SkillTemplate;
import java.io.ByteArrayOutputStream;
import nro.models.network.Message;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import nro.models.server.Manager;
import nro.models.network.MySession;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.utils.Logger;

import nro.models.player_system.Template.BgItem;

public class DataGame {

    private static final byte DEFAULT_MAP_VERSION = 10;
    private static final int MAX_SIGNED_TEMPLATE_COUNT = 127;
    private static final int MAX_PRELOADED_PLAYER_ICONS = 192;
    private static final int PLAYER_ICON_PRELOAD_RETRY_COUNT = 2;
    private static final int PLAYER_ICON_PRELOAD_RETRY_DELAY_MS = 900;
    private static final int[] INFINITY_CASTLE_MOB_IDS = {
        110, 111, 119, 120, 121, 122, 123, 124, 125, 126
    };
    private static final ExecutorService FISHING_ASSET_PRELOADER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "fishing-map-asset-preloader");
        thread.setDaemon(true);
        return thread;
    });
    private static final int[] FISHING_MAP_192_IMAGES = {
        872, 919, 920, 921, 922, 931, 932, 933, 934, 935, 936, 937, 938, 939, 940, 941,
        942, 943, 944, 945, 946, 947, 948, 949, 950, 951, 952, 953, 954, 955, 956, 957,
        958, 959, 960, 961, 962, 963, 964, 983, 984, 985, 986
    };
    private static final int[] FISHING_MAP_193_IMAGES = {
        919, 920, 989, 990, 991, 992, 993, 994, 995, 996, 997, 998, 999, 1000, 1001,
        1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 1010, 1011, 1012, 1013, 1014,
        1015, 1016, 1017, 1088, 1089, 1090, 1091, 1092, 1093, 1094, 1095
    };
    private static final int[] FISHING_MAP_194_IMAGES = {
        919, 920, 989, 1021, 1022, 1023, 1024, 1025, 1026, 1027, 1028, 1029, 1030,
        1031, 1032, 1033, 1034, 1035, 1036, 1037, 1038, 1039, 1040, 1041, 1042, 1043,
        1044, 1045, 1046, 1047, 1048, 1049, 1050, 1096, 1089, 1098, 1093, 1100, 1101,
        1102, 1103
    };
    private static final int[] FISHING_MAP_195_IMAGES = {
        919, 920, 989, 1054, 1055, 1056, 1058, 1060, 1061, 1062, 1001, 1065, 1066,
        1067, 1068, 1069, 1008, 1071, 1072, 1073, 1010, 1075, 1076, 1077, 1078, 1079,
        1080, 1081, 1084, 1085, 1087, 1104, 1105, 1106, 1107, 1100, 1109, 1093, 1099,
        1112
    };

    // 64 keeps the costume baseline fix and republishes deduplicated fishing backgrounds.
    public static byte vsData = 64;
    public static byte vsMap = loadMapVersion();
    // 2 publishes real skill descriptions instead of the old literal "null".
    public static byte vsSkill = 2;
    // 34 republishes compacted templates as one reload plus one append packet.
    public static byte vsItem = 34;
    public static int vsRes = 1;
    public static short maxSmallVersion = 32767;

    private static final Map<Byte, byte[]> SMALL_IMAGE_VERSIONS = new HashMap<>();
    private static final Map<Byte, byte[]> BG_IMAGE_VERSIONS = new HashMap<>();
    private static final Map<Byte, Integer> RESOURCE_VERSIONS = new HashMap<>();

    public static String LINK_IP_PORT = "Ngọc Rồng Online:36.50.134.190:14445:0";
    public static Map<Object, Object> MAP_MOUNT_NUM = new HashMap<>();

    public static void sendVersionGame(MySession session) {
        Message msg;
        try {
            msg = Service.gI().messageNotMap((byte) 4);
            msg.writer().writeByte(vsData);
            msg.writer().writeByte(vsMap);
            msg.writer().writeByte(vsSkill);
            msg.writer().writeByte(vsItem);
            msg.writer().writeByte(0);

            long[] smtieuchuan = {1000L, 3000L, 15000L, 40000L, 90000L, 170000L, 340000L, 700000L,
                1500000L, 15000000L, 150000000L, 1500000000L, 5000000000L, 10000000000L, 40000000000L,
                50010000000L, 60010000000L, 70010000000L, 80010000000L, 100010000000L, 1000010000000L, 10000010000000L};
            msg.writer().writeByte(smtieuchuan.length);
            for (int i = 0; i < smtieuchuan.length; i++) {
                msg.writer().writeLong(smtieuchuan[i]);
            }
            session.sendMessage(msg);
            msg.cleanup();
        } catch (IOException e) {
        }
    }

    //vData
    public static void updateData(MySession session) {
        final byte[] dart = FileIO.readFile("data/update_data/dart");
        final byte[] arrow = FileIO.readFile("data/update_data/arrow");
        final byte[] effect = FileIO.readFile("data/update_data/effect");
        final byte[] image = FileIO.readFile("data/update_data/image");
        final byte[] part = FileIO.readFile("data/update_data/part");
        final byte[] skill = FileIO.readFile("data/update_data/skill");

        Message msg;
        try {
            msg = new Message(-87);
            msg.writer().writeByte(vsData);
            msg.writer().writeInt(dart.length);
            msg.writer().write(dart);
            msg.writer().writeInt(arrow.length);
            msg.writer().write(arrow);
            msg.writer().writeInt(effect.length);
            msg.writer().write(effect);
            msg.writer().writeInt(image.length);
            msg.writer().write(image);
            msg.writer().writeInt(part.length);
            msg.writer().write(part);
            msg.writer().writeInt(skill.length);
            msg.writer().write(skill);

            session.doSendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //vMap
    public static void updateMap(MySession session) {
        Message msg;
        try {
            int mobTemplateCount = Manager.MOB_TEMPLATES.size();
            if (mobTemplateCount > MAX_SIGNED_TEMPLATE_COUNT) {
                throw new IllegalStateException(
                        "updateMap chỉ hỗ trợ tối đa " + MAX_SIGNED_TEMPLATE_COUNT
                                + " mob template; hiện có " + mobTemplateCount
                                + ". Client đọc count bằng byte có dấu và sẽ bỏ dở dữ liệu map."
                );
            }
            for (int id = 0; id < mobTemplateCount; id++) {
                MobTemplate temp = Manager.MOB_TEMPLATES.get(id);
                if (temp.id != id) {
                    throw new IllegalStateException(
                            "mob_template phải liên tục từ ID 0: vị trí " + id
                                    + " đang chứa ID " + temp.id
                    );
                }
            }
            msg = Service.gI().messageNotMap((byte) 6);
            msg.writer().writeByte(vsMap);
            String[] mapNames = buildMapNamesById(Manager.MAP_TEMPLATES);
            msg.writer().writeByte(mapNames.length);
            for (String mapName : mapNames) {
                msg.writer().writeUTF(mapName);
            }
            int maxNpcId = -1;
            for (NpcTemplate temp : Manager.NPC_TEMPLATES) {
                if (temp.id > maxNpcId) {
                    maxNpcId = temp.id;
                }
            }
            int totalNpcCount = maxNpcId + 1;
            msg.writer().writeByte(totalNpcCount);
            for (int id = 0; id <= maxNpcId; id++) {
                NpcTemplate temp = Manager.getNpcTemplate(id);
                if (temp != null) {
                    msg.writer().writeUTF(temp.name);
                    msg.writer().writeShort(temp.head);
                    msg.writer().writeShort(temp.body);
                    msg.writer().writeShort(temp.leg);
                    msg.writer().writeByte(0);
                } else {
                    msg.writer().writeUTF("NPC");
                    msg.writer().writeShort(0);
                    msg.writer().writeShort(0);
                    msg.writer().writeShort(0);
                    msg.writer().writeByte(0);
                }
            }
            msg.writer().writeByte(mobTemplateCount);
            for (MobTemplate temp : Manager.MOB_TEMPLATES) {
                msg.writer().writeByte(temp.type);
                msg.writer().writeUTF(temp.name);
                msg.writer().writeInt(temp.hp);
                msg.writer().writeByte(temp.rangeMove);
                msg.writer().writeByte(temp.speed);
                msg.writer().writeByte(temp.dartType);
            }
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(DataGame.class, e);
        }
    }

    static String[] buildMapNamesById(MapTemplate[] templates) {
        if (templates == null || templates.length == 0) {
            return new String[0];
        }

        int maxMapId = -1;
        for (MapTemplate template : templates) {
            if (template == null) {
                continue;
            }
            if (template.id < 0 || template.id > 254) {
                throw new IllegalStateException("Map ID ngoài giới hạn packet 0..254: " + template.id);
            }
            maxMapId = Math.max(maxMapId, template.id);
        }

        String[] names = new String[maxMapId + 1];
        for (MapTemplate template : templates) {
            if (template == null) {
                continue;
            }
            if (names[template.id] != null) {
                throw new IllegalStateException("Trùng map ID: " + template.id);
            }
            String name = template.name == null ? "" : template.name.trim();
            names[template.id] = name.isEmpty() ? "Map " + template.id : name;
        }
        for (int id = 0; id < names.length; id++) {
            if (names[id] == null) {
                names[id] = "Map " + id;
            }
        }
        return names;
    }

    static byte loadMapVersion() {
        File versionFile = new File("data/map/version.txt");
        if (!versionFile.isFile()) {
            return DEFAULT_MAP_VERSION;
        }
        try {
            String text = new String(Files.readAllBytes(versionFile.toPath()), java.nio.charset.StandardCharsets.UTF_8).trim();
            int version = Integer.parseInt(text);
            if (version < 1 || version > 127) {
                throw new IllegalArgumentException("Map version phải nằm trong khoảng 1..127: " + version);
            }
            return (byte) version;
        } catch (Exception e) {
            Logger.logException(DataGame.class, e, "Không thể đọc data/map/version.txt; dùng map version mặc định " + DEFAULT_MAP_VERSION);
            return DEFAULT_MAP_VERSION;
        }
    }

    //vSkill
    public static void updateSkill(MySession session) {
        Message msg;
        try {
            msg = new Message(-28);

            msg.writer().writeByte(7);
            msg.writer().writeByte(vsSkill);
            msg.writer().writeByte(0); //count skill option

            msg.writer().writeByte(Manager.NCLASS.size());
            for (NClass nClass : Manager.NCLASS) {
                msg.writer().writeUTF(nClass.name);

                msg.writer().writeByte(nClass.skillTemplatess.size());
                for (SkillTemplate skillTemp : nClass.skillTemplatess) {
                    msg.writer().writeByte(skillTemp.id);
                    msg.writer().writeUTF(skillTemp.name);
                    msg.writer().writeByte(skillTemp.maxPoint);
                    msg.writer().writeByte(skillTemp.manaUseType);
                    msg.writer().writeByte(skillTemp.type);
                    msg.writer().writeShort(skillTemp.iconId);
                    msg.writer().writeUTF(skillTemp.damInfo);
                    msg.writer().writeUTF(getSkillDescription(skillTemp));
                    if (skillTemp.id != 0) {
                        msg.writer().writeByte(skillTemp.skillss.size());
                        for (Skill skill : skillTemp.skillss) {
                            msg.writer().writeShort(skill.skillId);
                            msg.writer().writeByte(skill.point);
                            msg.writer().writeLong(skill.powRequire);
                            msg.writer().writeShort(skill.manaUse);
                            msg.writer().writeInt(skill.coolDown);
                            msg.writer().writeShort(skill.dx);
                            msg.writer().writeShort(skill.dy);
                            msg.writer().writeByte(skill.maxFight);
                            msg.writer().writeShort(skill.damage);
                            msg.writer().writeShort(skill.price);
                            msg.writer().writeUTF(skill.moreInfo);
                        }
                    } else {
                        //Thêm 2 skill trống 105, 106
                        msg.writer().writeByte(skillTemp.skillss.size() + 2);
                        for (Skill skill : skillTemp.skillss) {
                            msg.writer().writeShort(skill.skillId);
                            msg.writer().writeByte(skill.point);
                            msg.writer().writeLong(skill.powRequire);
                            msg.writer().writeShort(skill.manaUse);
                            msg.writer().writeInt(skill.coolDown);
                            msg.writer().writeShort(skill.dx);
                            msg.writer().writeShort(skill.dy);
                            msg.writer().writeByte(skill.maxFight);
                            msg.writer().writeShort(skill.damage);
                            msg.writer().writeShort(skill.price);
                            msg.writer().writeUTF(skill.moreInfo);
                        }
                        for (int i = 105; i <= 106; i++) {
                            msg.writer().writeShort(i);
                            msg.writer().writeByte(0);
                            msg.writer().writeLong(0);
                            msg.writer().writeShort(0);
                            msg.writer().writeInt(0);
                            msg.writer().writeShort(0);
                            msg.writer().writeShort(0);
                            msg.writer().writeByte(0);
                            msg.writer().writeShort(0);
                            msg.writer().writeShort(0);
                            msg.writer().writeUTF("");
                        }
                    }
                }
            }
            session.doSendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(DataGame.class, e);
        }
    }

    public static void sendDataImageVersion(MySession session) {
        Message msg;
        try {
        } catch (Exception e) {
            Logger.logException(DataGame.class, e);
        }
    }

    public static void sendEffectTemplate(MySession session, int id, int... idtemp) {
        if (!session.isAssetReady()) {
            return;
        }
        int idT = id;
        if (idtemp.length > 0 && idtemp[0] != 0) {
            idT = idtemp[0];
        }
        Message msg;
        try {
            final byte[] effData = FileIO.readFile("data/effdata/DataEffect_" + idT);
            final byte[] effImg = FileIO.readFile("data/effect/x" + session.zoomLevel + "/ImgEffect_" + idT + ".png");
            if (effData == null || effImg == null) {
                return;
            }
            msg = new Message(-66);
            msg.writer().writeShort(id);
            msg.writer().writeInt(effData.length);
            msg.writer().write(effData);
            if (session.version > 220) {
                msg.writer().write(idT == 60 ? 2 : 0);
            }
            msg.writer().writeInt(effImg.length);
            msg.writer().write(effImg);
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendBgItemVersion(MySession session) {
        if (!session.isAssetReady()) {
            return;
        }
        Message msg;
        try {
            byte[] versions = getBgImageVersions(session.zoomLevel);
            msg = new Message(-93);
            msg.writer().writeShort(versions.length);
            msg.writer().write(versions);
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(DataGame.class, e);
        }
    }

    public static void sendItemBGTemplate(MySession session, int id) {
        if (!session.isAssetReady()) {
            return;
        }
        Message msg;
        try {
            final byte[] bg_temp = FileIO.readFile("data/item_bg_temp/x" + session.zoomLevel + "/" + id + ".png");
            if (bg_temp == null) {
                return;
            }
            msg = new Message(-32);
            msg.writer().writeShort(id);
            msg.writer().writeInt(bg_temp.length);
            msg.writer().write(bg_temp);
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(DataGame.class, e);
        }
    }

    /**
     * Nội dung giới thiệu nằm ngay dưới tên skill trong bảng chi tiết. Các
     * con số theo cấp vẫn lấy từ damInfo và Skill để không bị lặp hoặc sai khi
     * quản trị viên cân bằng lại chỉ số.
     */
    private static String getSkillDescription(SkillTemplate skillTemplate) {
        if (skillTemplate == null) {
            return "";
        }
        return switch (skillTemplate.id) {
            case Skill.DRAGON -> "Đòn đánh cận chiến cơ bản của người Trái Đất.";
            case Skill.KAMEJOKO -> "Bắn một luồng năng lượng Kamejoko tấn công mục tiêu từ xa.";
            case Skill.DEMON -> "Đòn đánh cận chiến cơ bản của chiến binh Namếc.";
            case Skill.MASENKO -> "Bắn chưởng Masenko tầm xa, gây sát thương lên mục tiêu.";
            case Skill.GALICK -> "Đòn đánh cận chiến cơ bản của chiến binh Xayda.";
            case Skill.ANTOMIC -> "Bắn chưởng Antomic tầm xa với sức công phá mạnh.";
            case Skill.THAI_DUONG_HA_SAN -> "Phát ra ánh sáng cực mạnh, làm choáng kẻ địch trong phạm vi tác dụng.";
            case Skill.TRI_THUONG -> "Hồi phục HP và KI cho bản thân cùng đồng đội ở gần.";
            case Skill.TAI_TAO_NANG_LUONG -> "Vận công để tái tạo HP và KI liên tục theo thời gian.";
            case Skill.KAIOKEN -> "Lao đến mục tiêu và tung chuỗi đòn Kaioken cận chiến.";
            case Skill.QUA_CAU_KENH_KHI -> "Tích tụ quả cầu năng lượng rồi phát nổ, gây sát thương diện rộng.";
            case Skill.MAKANKOSAPPO -> "Tụ lực và bắn tia Makankosappo xuyên phá mục tiêu.";
            case Skill.DE_TRUNG -> "Tạo một quái đệ tử đi theo và hỗ trợ chiến đấu.";
            case Skill.BIEN_KHI -> "Biến thành khỉ, tăng sức đánh, HP và tốc độ di chuyển.";
            case Skill.TU_SAT -> "Hy sinh bản thân để tạo vụ nổ gây sát thương lớn cho kẻ địch xung quanh.";
            case Skill.LIEN_HOAN -> "Tấn công mục tiêu liên tiếp bằng một chuỗi đòn cận chiến.";
            case Skill.SOCOLA -> "Biến mục tiêu thành Sôcôla và tạm thời vô hiệu hóa khả năng chiến đấu.";
            case Skill.KHIEN_NANG_LUONG -> "Tạo khiên năng lượng giúp vô hiệu hóa các đòn tấn công nhận vào.";
            case Skill.DICH_CHUYEN_TUC_THOI -> "Dịch chuyển tức thời đến mục tiêu, gây sát thương và làm choáng.";
            case Skill.HUYT_SAO -> "Tăng HP tối đa tạm thời cho bản thân và mọi đồng đội xung quanh.";
            case Skill.THOI_MIEN -> "Đưa mục tiêu vào trạng thái ngủ và tạm thời không thể hành động.";
            case Skill.TROI -> "Trói chặt mục tiêu, khiến đối phương tạm thời không thể di chuyển.";
            case Skill.SUPER_KAME -> "Tụ lực thi triển Super Kamejoko với sức sát thương rất lớn.";
            case Skill.LIEN_HOAN_CHUONG -> "Tung liên tiếp nhiều đòn chưởng năng lượng vào mục tiêu.";
            case Skill.MA_PHONG_BA -> "Thi triển Ma Phong Ba để nhốt đối thủ vào bình chứa.";
            default -> "";
        };
    }

    public static void preloadInfinityCastleAssets(MySession session) {
        if (session == null || !session.markInfinityCastleAssetsSent()) {
            return;
        }
        // Some clients do not request every custom mob after their cached map
        // data changes. Proactively replace all Infinity Castle MobTemplate.data
        // entries so a missing request cannot leave only the default shadow.
        for (int mobId : INFINITY_CASTLE_MOB_IDS) {
            requestMobTemplate(session, mobId);
        }

        // Both background halves are shared by maps 187..190. Send them before
        // map-info so the client never falls back to the default Earth backdrop.
        sendItemBGTemplate(session, 516);
        sendItemBGTemplate(session, 565);
    }

    /**
     * Downloads the next fishing scene while the player is still walking in
     * the current one. Images are throttled and sent only once per session so
     * entering a map does not compete with a large burst of background data.
     */
    public static void preloadNextFishingMapAssets(MySession session, int currentMapId) {
        int nextMapId;
        int[] imageIds;
        switch (currentMapId) {
            case 186 -> {
                nextMapId = 192;
                imageIds = FISHING_MAP_192_IMAGES;
            }
            case 192 -> {
                nextMapId = 193;
                imageIds = FISHING_MAP_193_IMAGES;
            }
            case 193 -> {
                nextMapId = 194;
                imageIds = FISHING_MAP_194_IMAGES;
            }
            case 194 -> {
                nextMapId = 195;
                imageIds = FISHING_MAP_195_IMAGES;
            }
            default -> {
                return;
            }
        }
        if (session == null || !session.markFishingMapAssetsSent(nextMapId)) {
            return;
        }
        FISHING_ASSET_PRELOADER.execute(() -> {
            try {
                // A background request proves this client is missing at least
                // part of the fishing asset set. Fully cached clients should
                // not receive tens of megabytes of unnecessary push data.
                for (int retry = 0; retry < 20 && session.isConnected()
                        && !session.hasFishingBackgroundRequest(); retry++) {
                    Thread.sleep(250L);
                }
                if (!session.hasFishingBackgroundRequest()) {
                    return;
                }
                for (int imageId : imageIds) {
                    if (!session.isConnected()) {
                        return;
                    }
                    while (session.getNumMessages() > 8 && session.isConnected()) {
                        Thread.sleep(200L);
                    }
                    sendItemBGTemplate(session, imageId);
                    Thread.sleep(120L);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Exception exception) {
                Logger.logException(DataGame.class, exception, "Cannot preload fishing map assets");
            }
        });
    }

    public static void sendDataItemBG(MySession session) {
        Message msg;
        try {
            BgItem[] bgItemsById = buildBgItemTable();
            msg = new Message(-31);
            msg.writer().writeShort(bgItemsById.length);
            for (BgItem bgItem : bgItemsById) {
                // The client treats the array position as bg_item_template.id.
                // Preserve sparse database IDs with harmless placeholders so
                // runtime map binaries never resolve to the wrong image/layer.
                msg.writer().writeShort(bgItem == null ? 0 : bgItem.idImage);
                msg.writer().writeByte(bgItem == null ? 1 : bgItem.layer);
                msg.writer().writeShort(bgItem == null ? 0 : bgItem.dx);
                msg.writer().writeShort(bgItem == null ? 0 : bgItem.dy);
                msg.writer().writeByte(0);
            }
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendIcon(MySession session, int id) {
        if (!session.isAssetReady()) {
            return;
        }
        Message msg;
        try {
            final byte[] icon = FileIO.readFile("data/icon/x" + session.zoomLevel + "/" + id + ".png");

            if (icon == null) {
                return;
            }

            msg = new Message(-67);
            msg.writer().writeInt(id);
            msg.writer().writeInt(icon.length);
            msg.writer().write(icon);
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * The Unity client stops its on-demand small-image queue after a limited
     * number of requests when the cache has just been rebuilt. Send the icons
     * referenced by the initial player packet proactively, in the same order,
     * so later bag rows cannot remain blank until another reconnect.
     */
    public static void preloadPlayerItemIcons(Player player) {
        if (player == null || player.inventory == null || player.getSession() == null
                || !player.getSession().isAssetReady()) {
            return;
        }

        Set<Integer> iconIds = new LinkedHashSet<>();
        collectItemIcons(player.inventory.itemsBody, iconIds);
        collectItemIcons(player.inventory.itemsBag, iconIds);
        sendItemIcons(player.getSession(), iconIds);
    }

    public static void preloadPlayerBagIcons(Player player) {
        if (player == null || player.inventory == null || player.getSession() == null
                || !player.getSession().isAssetReady()) {
            return;
        }

        Set<Integer> iconIds = new LinkedHashSet<>();
        collectItemIcons(player.inventory.itemsBag, iconIds);
        sendItemIcons(player.getSession(), iconIds);
    }

    public static void preloadPlayerItemIconsWithRetry(Player player) {
        preloadPlayerItemIcons(player);
        if (player == null || player.getSession() == null || !player.getSession().isAssetReady()) {
            return;
        }

        Thread worker = new Thread(() -> {
            for (int i = 0; i < PLAYER_ICON_PRELOAD_RETRY_COUNT; i++) {
                try {
                    Thread.sleep((long) PLAYER_ICON_PRELOAD_RETRY_DELAY_MS * (i + 1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                MySession session = player.getSession();
                if (session == null || !session.isConnected() || !session.isAssetReady()) {
                    return;
                }
                preloadPlayerItemIcons(player);
            }
        }, "player-icon-preload-" + player.id);
        worker.setDaemon(true);
        worker.start();
    }

    private static void sendItemIcons(MySession session, Set<Integer> iconIds) {
        int sent = 0;
        for (int iconId : iconIds) {
            if (sent >= MAX_PRELOADED_PLAYER_ICONS) {
                break;
            }
            sendIcon(session, iconId);
            sent++;
        }
    }

    private static void collectItemIcons(Iterable<Item> items, Set<Integer> iconIds) {
        if (items == null) {
            return;
        }

        if (items instanceof java.util.Collection<?>) {
            for (Object item : ((java.util.Collection<?>) items).toArray()) {
                collectItemIcon((Item) item, iconIds);
            }
            return;
        }

        for (Item item : items) {
            collectItemIcon(item, iconIds);
        }
    }

    private static void collectItemIcon(Item item, Set<Integer> iconIds) {
        if (item != null && item.template != null && item.template.iconID >= 0) {
            iconIds.add((int) item.template.iconID);
        }
    }

    public static void sendSmallVersion(MySession session) {
        if (!session.isAssetReady()) {
            return;
        }
        Message msg;
        try {
            byte[] versions = getSmallImageVersions(session.zoomLevel);
            msg = new Message(-77);
            msg.writer().writeShort(versions.length);
            msg.writer().write(versions);
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(DataGame.class, e);
        }
    }

    private static synchronized byte[] getSmallImageVersions(byte zoomLevel) {
        return SMALL_IMAGE_VERSIONS.computeIfAbsent(zoomLevel,
                zoom -> buildNumericFileVersions(new File("data/icon/x" + zoom), maxSmallVersion));
    }

    private static synchronized byte[] getBgImageVersions(byte zoomLevel) {
        return BG_IMAGE_VERSIONS.computeIfAbsent(zoomLevel, zoom -> {
            int maxImageId = -1;
            for (BgItem bgItem : Manager.BG_ITEMS) {
                if (bgItem.idImage > maxImageId) {
                    maxImageId = bgItem.idImage;
                }
            }
            return buildNumericFileVersions(new File("data/item_bg_temp/x" + zoom), maxImageId + 1);
        });
    }

    static byte[] buildNumericFileVersions(File directory, int size) {
        byte[] versions = new byte[Math.max(0, size)];
        File[] files = listRegularFiles(directory);
        for (File file : files) {
            String fileName = file.getName();
            int extensionIndex = fileName.lastIndexOf('.');
            String idText = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
            try {
                int id = Integer.parseInt(idText);
                if (id >= 0 && id < versions.length) {
                    versions[id] = fileVersion(file);
                }
            } catch (NumberFormatException ignored) {
                // Các file animation dạng 41$1.png không phải một image_id độc lập.
            }
        }
        return versions;
    }

    static byte fileVersion(File file) {
        if (file == null || !file.isFile()) {
            return 0;
        }

        CRC32 crc = new CRC32();
        byte[] buffer = new byte[8192];
        try (InputStream input = Files.newInputStream(file.toPath())) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                crc.update(buffer, 0, read);
            }
            // 0 được dành cho asset không tồn tại trong packet version.
            return (byte) ((crc.getValue() % 126) + 1);
        } catch (IOException e) {
            Logger.logException(DataGame.class, e, "Không thể tính CRC asset: " + file.getPath());
            return 0;
        }
    }

    static BgItem[] buildBgItemTable() {
        int maxTemplateId = -1;
        for (BgItem bgItem : Manager.BG_ITEMS) {
            if (bgItem != null && bgItem.id > maxTemplateId) {
                maxTemplateId = bgItem.id;
            }
        }
        BgItem[] bgItemsById = new BgItem[maxTemplateId + 1];
        for (BgItem bgItem : Manager.BG_ITEMS) {
            if (bgItem != null && bgItem.id >= 0 && bgItem.id < bgItemsById.length) {
                bgItemsById[bgItem.id] = bgItem;
            }
        }
        return bgItemsById;
    }

    public static void requestMobTemplate(MySession session, int id) {
        if (!session.isAssetReady()) {
            return;
        }
        Message msg;
        try {
//            if (!session.check && id > 106) {
//                byte[] mob = FileIO.readFile("data/mob/x" + session.zoomLevel + "/" + 0);
//                msg = new Message(11);
//                msg.writer().writeByte(id);
//                msg.writer().write(mob);
//                session.sendMessage(msg);
//                msg.cleanup();
//                return;
//            }
            final byte[] mob = FileIO.readFile("data/mob/x" + session.zoomLevel + "/" + id);
            if (mob == null) {
                Logger.errorln("[MobAsset] Missing mob asset ID " + id + " x" + session.zoomLevel);
                return;
            }
            msg = new Message(11);
            msg.writer().writeByte(id);
            msg.writer().write(mob);
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(DataGame.class, e, "Cannot send mob asset ID " + id);
        }
    }

    public static void sendTileSetInfo(MySession session) {
        Message msg;
        try {
            final byte[] data = FileIO.readFile("data/map/tile_set_info");
            msg = new Message(-82);
            msg.writer().write(data);
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //data vẽ map
    public static void sendMapTemp(MySession session, int id) {
        Message msg;
        try {
            final byte[] data = FileIO.readFile("data/map/tile_map_data/" + id);
            if (data == null) {
                return;
            }
            msg = new Message(-28);
            msg.writer().writeByte(10);
            msg.writer().write(data);
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(DataGame.class, e);
        }
    }

    //head-avatar
    public static void sendHeadAvatar(Message msg) {
        try {
            msg.writer().writeShort(Manager.HEAD_AVATARS.size());
            for (HeadAvatar ha : Manager.HEAD_AVATARS) {
                msg.writer().writeShort(ha.headId);
                msg.writer().writeShort(ha.avatarId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendImageByName(MySession session, String imgName) {
        if (!session.isAssetReady()) {
            return;
        }
        Message msg;
        try {
            msg = new Message(66);
            msg.writer().writeUTF(imgName);
            msg.writer().writeByte(Manager.getNFrameImageByName(imgName));

            final byte[] data = FileIO.readFile("data/img_by_name/x" + session.zoomLevel + "/" + imgName + ".png");

            if (data == null) {
                msg.writer().writeInt(0);
            } else {
                msg.writer().writeInt(data.length);
                msg.writer().write(data);
            }

            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendVersionRes(MySession session) {
        if (!session.isAssetReady()) {
            return;
        }
        Message msg;
        try {
            msg = new Message(-74);
            msg.writer().writeByte(0);
            msg.writer().writeInt(getResourceVersion(session.zoomLevel));
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(DataGame.class, e);
        }
    }

    public static void sendSizeRes(MySession session) {
        if (!session.isAssetReady()) {
            return;
        }
        Message msg;
        try {
            msg = new Message(-74);
            msg.writer().writeByte(1);
            final File[] files = listRegularFiles(new File("data/res/x" + session.zoomLevel));
            msg.writer().writeShort(files.length);
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(DataGame.class, e);
        }
    }

    public static void sendRes(MySession session) {
        if (!session.isAssetReady()) {
            return;
        }
        Message msg;
        try {
            File dir = new File("data/res/x" + session.zoomLevel);
            File[] files = listRegularFiles(dir);
            for (final File fileEntry : files) {
                String original = fileEntry.getName();
                try (FileChannel fileChannel = FileChannel.open(fileEntry.toPath(), StandardOpenOption.READ)) {
                    ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    int bytesRead;
                    while ((bytesRead = fileChannel.read(buffer)) > 0) {
                        buffer.flip();
                        byteArrayOutputStream.write(buffer.array(), 0, bytesRead);
                        buffer.clear();
                    }
                    byte[] res = byteArrayOutputStream.toByteArray();
                    msg = new Message(-74);
                    msg.writer().writeByte(2);
                    msg.writer().writeUTF(original);
                    msg.writer().writeInt(res.length);
                    msg.writer().write(res);
                    session.sendMessage(msg);
                    msg.cleanup();
                } catch (IOException e) {
                    Logger.logException(DataGame.class, e);
                }
            }
            msg = new Message(-74);
            msg.writer().writeByte(3);
            msg.writer().writeInt(getResourceVersion(session.zoomLevel));
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            Logger.logException(DataGame.class, e);
        }
    }

    private static File[] listRegularFiles(File directory) {
        File[] files = directory.listFiles(File::isFile);
        if (files == null) {
            return new File[0];
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        return files;
    }

    private static synchronized int getResourceVersion(byte zoomLevel) {
        return RESOURCE_VERSIONS.computeIfAbsent(zoomLevel, zoom -> {
            CRC32 crc = new CRC32();
            crc.update(vsRes);
            byte[] buffer = new byte[8192];
            for (File file : listRegularFiles(new File("data/res/x" + zoom))) {
                byte[] name = file.getName().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                crc.update(name, 0, name.length);
                try (InputStream input = java.nio.file.Files.newInputStream(file.toPath())) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        crc.update(buffer, 0, read);
                    }
                } catch (IOException e) {
                    Logger.logException(DataGame.class, e);
                }
            }
            int version = (int) (crc.getValue() & 0x7FFFFFFF);
            return version == 0 ? 1 : version;
        });
    }

    public static void sendLinkIP(MySession session) {
        Message msg;
        try {
            msg = new Message(-29);
            msg.writer().writeByte(2);
            msg.writer().writeUTF(LINK_IP_PORT + ",0,0");
            msg.writer().writeByte(1);
            session.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
