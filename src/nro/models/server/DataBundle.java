package nro.models.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nro.models.clan.Clan;
import nro.models.intrinsic.Intrinsic;
import nro.models.item.Item.ItemOption;
import nro.models.player_badges.BagesTemplate;
import nro.models.player_system.Template.AchievementTemplate;
import nro.models.player_system.Template.ArrHead2Frames;
import nro.models.player_system.Template.BgItem;
import nro.models.player_system.Template.FlagBag;
import nro.models.player_system.Template.HeadAvatar;
import nro.models.player_system.Template.ItemOptionTemplate;
import nro.models.player_system.Template.ItemTemplate;
import nro.models.player_system.Template.MapTemplate;
import nro.models.player_system.Template.MobTemplate;
import nro.models.player_system.Template.NpcTemplate;
import nro.models.shop.Shop;
import nro.models.skill.NClass;
import nro.models.task.BadgesTaskTemplate;
import nro.models.task.ClanTaskTemplate;
import nro.models.task.SideTaskTemplate;
import nro.models.task.TaskMain;

/** Complete off-side result of one template/database load. */
public final class DataBundle {

    private final long revision;
    private final MapTemplate[] mapTemplates;
    private final List<ItemOptionTemplate> itemOptionTemplates;
    private final List<ArrHead2Frames> headFrames;
    private final Map<String, Byte> imagesByName;
    private final List<ItemTemplate> itemTemplates;
    private final Map<Short, List<ItemOption>> defaultItemOptions;
    private final List<MobTemplate> mobTemplates;
    private final List<NpcTemplate> npcTemplates;
    private final List<TaskMain> tasks;
    private final List<SideTaskTemplate> sideTasks;
    private final List<ClanTaskTemplate> clanTasks;
    private final List<AchievementTemplate> achievements;
    private final List<Intrinsic> intrinsics;
    private final List<Intrinsic> intrinsicEarth;
    private final List<Intrinsic> intrinsicNamek;
    private final List<Intrinsic> intrinsicSaiyan;
    private final List<HeadAvatar> headAvatars;
    private final List<BgItem> backgroundItems;
    private final List<FlagBag> flagBags;
    private final List<NClass> classes;
    private final List<Shop> shops;
    private final List<String> notifications;
    private final List<BadgesTaskTemplate> badgeTasks;
    private final List<BagesTemplate> badges;
    private final List<Clan> clans;

    DataBundle(long revision, MapTemplate[] mapTemplates,
            List<ItemOptionTemplate> itemOptionTemplates, List<ArrHead2Frames> headFrames,
            Map<String, Byte> imagesByName, List<ItemTemplate> itemTemplates,
            Map<Short, List<ItemOption>> defaultItemOptions, List<MobTemplate> mobTemplates,
            List<NpcTemplate> npcTemplates, List<TaskMain> tasks,
            List<SideTaskTemplate> sideTasks, List<ClanTaskTemplate> clanTasks,
            List<AchievementTemplate> achievements, List<Intrinsic> intrinsics,
            List<Intrinsic> intrinsicEarth, List<Intrinsic> intrinsicNamek,
            List<Intrinsic> intrinsicSaiyan, List<HeadAvatar> headAvatars,
            List<BgItem> backgroundItems, List<FlagBag> flagBags, List<NClass> classes,
            List<Shop> shops, List<String> notifications,
            List<BadgesTaskTemplate> badgeTasks, List<BagesTemplate> badges, List<Clan> clans) {
        this.revision = revision;
        this.mapTemplates = mapTemplates == null ? new MapTemplate[0] : mapTemplates.clone();
        this.itemOptionTemplates = copy(itemOptionTemplates);
        this.headFrames = copy(headFrames);
        this.imagesByName = imagesByName == null ? Map.of() : Map.copyOf(imagesByName);
        this.itemTemplates = copy(itemTemplates);
        this.defaultItemOptions = copyNested(defaultItemOptions);
        this.mobTemplates = copy(mobTemplates);
        this.npcTemplates = copy(npcTemplates);
        this.tasks = copy(tasks);
        this.sideTasks = copy(sideTasks);
        this.clanTasks = copy(clanTasks);
        this.achievements = copy(achievements);
        this.intrinsics = copy(intrinsics);
        this.intrinsicEarth = copy(intrinsicEarth);
        this.intrinsicNamek = copy(intrinsicNamek);
        this.intrinsicSaiyan = copy(intrinsicSaiyan);
        this.headAvatars = copy(headAvatars);
        this.backgroundItems = copy(backgroundItems);
        this.flagBags = copy(flagBags);
        this.classes = copy(classes);
        this.shops = copy(shops);
        this.notifications = copy(notifications);
        this.badgeTasks = copy(badgeTasks);
        this.badges = copy(badges);
        this.clans = copy(clans);
    }

    public static DataBundle empty() {
        return new DataBundle(0, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    public static DataBundle forTesting(long revision, List<ItemTemplate> items,
            List<ItemOptionTemplate> options, List<String> notifications) {
        return forTesting(revision, items, options, List.of(), notifications);
    }

    public static DataBundle forTesting(long revision, List<ItemTemplate> items,
            List<ItemOptionTemplate> options, List<AchievementTemplate> achievements,
            List<String> notifications) {
        return new DataBundle(revision, null, options, null, null, items, null, null, null,
                null, null, null, achievements, null, null, null, null, null, null, null, null, null,
                notifications, null, null, null);
    }

    private static <T> List<T> copy(List<T> source) {
        return source == null ? List.of() : List.copyOf(source);
    }

    private static Map<Short, List<ItemOption>> copyNested(Map<Short, List<ItemOption>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<Short, List<ItemOption>> copy = new HashMap<>();
        source.forEach((key, value) -> copy.put(key, value == null ? List.of() : List.copyOf(value)));
        return Collections.unmodifiableMap(copy);
    }

    public long revision() { return revision; }
    public MapTemplate[] mapTemplates() { return mapTemplates.clone(); }
    public List<ItemOptionTemplate> itemOptionTemplates() { return itemOptionTemplates; }
    public List<ArrHead2Frames> headFrames() { return headFrames; }
    public Map<String, Byte> imagesByName() { return imagesByName; }
    public List<ItemTemplate> itemTemplates() { return itemTemplates; }
    public Map<Short, List<ItemOption>> defaultItemOptions() { return defaultItemOptions; }
    public List<MobTemplate> mobTemplates() { return mobTemplates; }
    public List<NpcTemplate> npcTemplates() { return npcTemplates; }
    public List<TaskMain> tasks() { return tasks; }
    public List<SideTaskTemplate> sideTasks() { return sideTasks; }
    public List<ClanTaskTemplate> clanTasks() { return clanTasks; }
    public List<AchievementTemplate> achievements() { return achievements; }
    public List<Intrinsic> intrinsics() { return intrinsics; }
    public List<Intrinsic> intrinsicEarth() { return intrinsicEarth; }
    public List<Intrinsic> intrinsicNamek() { return intrinsicNamek; }
    public List<Intrinsic> intrinsicSaiyan() { return intrinsicSaiyan; }
    public List<HeadAvatar> headAvatars() { return headAvatars; }
    public List<BgItem> backgroundItems() { return backgroundItems; }
    public List<FlagBag> flagBags() { return flagBags; }
    public List<NClass> classes() { return classes; }
    public List<Shop> shops() { return shops; }
    public List<String> notifications() { return notifications; }
    public List<BadgesTaskTemplate> badgeTasks() { return badgeTasks; }
    public List<BagesTemplate> badges() { return badges; }
    public List<Clan> clans() { return clans; }
}
