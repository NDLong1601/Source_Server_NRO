package models;

import java.util.ArrayList;
import java.util.List;
import nro.models.map.WayPoint;
import nro.models.skill.Skill;

public class Template {

    public static class ItemOptionTemplate {

        public int id;

        public String name;

        public int type;

        public ItemOptionTemplate() {
        }

        public ItemOptionTemplate(int id, String name, int type) {
            this.id = id;
            this.name = name;
            this.type = type;
        }
    }

    public static class ItemTemplate {

        public short id;

        public byte type;

        public byte gender;

        public String name;

        public String description;

        public byte level;

        public short iconID;

        public short part;

        public boolean isUpToUp;

        public int strRequire;

        public int gold;

        public int gem;

        public int ruby;

        public int head;

        public int body;

        public int leg;

        public ItemTemplate() {
        }

        public ItemTemplate(short id, byte type, byte gender, String name, String description, short iconID, short part, boolean isUpToUp, int strRequire) {
            this.id = id;
            this.type = type;
            this.gender = gender;
            this.name = name;
            this.description = description;
            this.iconID = iconID;
            this.part = part;
            this.isUpToUp = isUpToUp;
            this.strRequire = strRequire;
        }
    }

    public static class MobTemplate {

        public int id;
        public byte type;
        public String name;
        public int hp;
        public byte rangeMove;
        public byte speed;
        public byte dartType;
        public byte percentDame;
        public byte percentTiemNang;
    }

    public static class NpcTemplate {

        public int id;
        public String name;
        public int head;
        public int body;
        public int leg;
        public int avatar;
    }

    public static class MapTemplate {

        public int id;
        public String name;

        public byte type;
        public byte planetId;
        public byte bgType;
        public byte tileId;
        public byte bgId;

        public byte zones;
        public byte maxPlayerPerZone;
        public List<WayPoint> wayPoints;

        public byte[] mobTemp;
        public byte[] mobLevel;
        public int[] mobHp;
        public short[] mobX;
        public short[] mobY;

        public byte[] npcId;
        public short[] npcX;
        public short[] npcY;

        public MapTemplate() {
            this.wayPoints = new ArrayList<>();
        }
    }

    public static class SkillTemplate {

        public byte id;

        public int classId;

        public String name;

        public int maxPoint;

        public int manaUseType;

        public int type;

        public int iconId;

        public String[] description;

        public Skill[] skills;

        public List<Skill> skillss = new ArrayList<>();

        public String damInfo;
    }

    public static class Part {

        public int id;

        public int type;

        public List<PartDetail> partDetails;

        public Part() {
            this.partDetails = new ArrayList();
        }
    }

    public static class PartDetail {

        public int iconId;

        public byte dx;

        public byte dy;

        public PartDetail(int iconId, byte dx, byte dy) {
            this.iconId = iconId;
            this.dx = dx;
            this.dy = dy;
        }
    }

    public static class HeadAvatar {

        public int headId;

        public int avatarId;

        public HeadAvatar(int headId, int avatarId) {
            this.headId = headId;
            this.avatarId = avatarId;
        }
    }

    public static class FlagBag {

        public int id;
        public short iconId;
        public short[] iconEffect;
        public String name;
        public int gold;
        public int gem;
    }

    public static class BgItem {

        public int id;

        public short idImage;

        public short dx;

        public short dy;

        public byte layer;
    }

    public static class ArrHead2Frames {

        public List<Integer> frames = new ArrayList();

    }

    public static class TileSetInfo {

        public List<TileSetInfoData> tileSetInfoData;

        public TileSetInfo(List<TileSetInfoData> tileSetInfoData) {
            this.tileSetInfoData = tileSetInfoData;
        }
    }

    public static class TileSetInfoData {

        public int type;
        public int[] data;

        public TileSetInfoData(int type, int[] data) {
            this.type = type;
            this.data = data;
        }
    }

    public static class WaitSuperRank {

        public long playerId;
        public long rivalId;

        public WaitSuperRank(long playerId, long rivalId) {
            this.playerId = playerId;
            this.rivalId = rivalId;
        }
    }

    public static class AchievementTemplate {

        public String info1;
        public String info2;
        public int money;
        public long maxCount;

        public AchievementTemplate(String info1, String info2, int money, long maxCount) {
            this.info1 = info1;
            this.info2 = info2;
            this.money = money;
            this.maxCount = maxCount;
        }
    }

    public static class AchievementQuest {

        public long completed;
        public boolean isRecieve;

        public AchievementQuest(long completed, boolean isRecieve) {
            this.completed = completed;
            this.isRecieve = isRecieve;
        }
    }

}
