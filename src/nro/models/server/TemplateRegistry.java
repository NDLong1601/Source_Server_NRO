package nro.models.server;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import nro.models.intrinsic.Intrinsic;
import nro.models.item.Item.ItemOption;
import nro.models.player_badges.BagesTemplate;
import nro.models.player_system.Template.*;
import nro.models.shop.Shop;
import nro.models.skill.NClass;
import nro.models.task.BadgesTaskTemplate;
import nro.models.task.ClanTaskTemplate;
import nro.models.task.SideTaskTemplate;
import nro.models.task.TaskMain;

/** Atomically published read-only template/catalog registry. */
public final class TemplateRegistry {

    private final AtomicReference<Snapshot> current = new AtomicReference<>(Snapshot.from(DataBundle.empty()));

    public void publish(DataBundle bundle) {
        Snapshot candidate = Snapshot.from(bundle);
        validateIndexedItems(candidate.itemTemplates());
        validateIndexedOptions(candidate.itemOptionTemplates());
        current.set(candidate);
    }

    private static void validateIndexedOptions(List<ItemOptionTemplate> options) {
        for (int index = 0; index < options.size(); index++) {
            ItemOptionTemplate option = options.get(index);
            if (option == null || option.id != index) {
                throw new IllegalArgumentException("item_option_template must be contiguous from ID 0; index=" + index);
            }
        }
    }

    private static void validateIndexedItems(List<ItemTemplate> items) {
        for (int index = 0; index < items.size(); index++) {
            ItemTemplate item = items.get(index);
            if (item == null || item.id != index) {
                throw new IllegalArgumentException("item_template must be contiguous from ID 0; index=" + index);
            }
        }
    }

    public Snapshot snapshot() { return current.get(); }
    public long revision() { return snapshot().revision(); }
    public MapTemplate[] mapTemplates() { return snapshot().mapTemplates(); }
    public List<ItemOptionTemplate> itemOptionTemplates() { return snapshot().itemOptionTemplates(); }
    public List<ArrHead2Frames> headFrames() { return snapshot().headFrames(); }
    public List<ItemTemplate> itemTemplates() { return snapshot().itemTemplates(); }
    public Map<Short, List<ItemOption>> defaultItemOptions() { return snapshot().defaultItemOptions(); }
    public List<MobTemplate> mobTemplates() { return snapshot().mobTemplates(); }
    public List<NpcTemplate> npcTemplates() { return snapshot().npcTemplates(); }
    public List<TaskMain> tasks() { return snapshot().tasks(); }
    public List<SideTaskTemplate> sideTasks() { return snapshot().sideTasks(); }
    public List<ClanTaskTemplate> clanTasks() { return snapshot().clanTasks(); }
    public List<AchievementTemplate> achievements() { return snapshot().achievements(); }
    public List<Intrinsic> intrinsics() { return snapshot().intrinsics(); }
    public List<Intrinsic> intrinsicEarth() { return snapshot().intrinsicEarth(); }
    public List<Intrinsic> intrinsicNamek() { return snapshot().intrinsicNamek(); }
    public List<Intrinsic> intrinsicSaiyan() { return snapshot().intrinsicSaiyan(); }
    public List<HeadAvatar> headAvatars() { return snapshot().headAvatars(); }
    public List<BgItem> backgroundItems() { return snapshot().backgroundItems(); }
    public List<FlagBag> flagBags() { return snapshot().flagBags(); }
    public List<NClass> classes() { return snapshot().classes(); }
    public List<Shop> shops() { return snapshot().shops(); }
    public List<String> notifications() { return snapshot().notifications(); }
    public List<BadgesTaskTemplate> badgeTasks() { return snapshot().badgeTasks(); }
    public List<BagesTemplate> badges() { return snapshot().badges(); }

    public ItemTemplate item(int id) {
        List<ItemTemplate> values = itemTemplates();
        return id >= 0 && id < values.size() ? values.get(id) : null;
    }

    public MobTemplate mob(int id) {
        for (MobTemplate value : mobTemplates()) {
            if (value.id == id) return value;
        }
        return null;
    }

    public NpcTemplate npc(int id) {
        for (NpcTemplate value : npcTemplates()) {
            if (value.id == id) return value;
        }
        return null;
    }

    public byte imageFrameCount(String name) {
        return snapshot().imagesByName().getOrDefault(name, (byte) 0);
    }

    public record Snapshot(long revision, MapTemplate[] mapTemplates,
            List<ItemOptionTemplate> itemOptionTemplates, List<ArrHead2Frames> headFrames,
            Map<String, Byte> imagesByName, List<ItemTemplate> itemTemplates,
            Map<Short, List<ItemOption>> defaultItemOptions, List<MobTemplate> mobTemplates,
            List<NpcTemplate> npcTemplates, List<TaskMain> tasks, List<SideTaskTemplate> sideTasks,
            List<ClanTaskTemplate> clanTasks, List<AchievementTemplate> achievements,
            List<Intrinsic> intrinsics, List<Intrinsic> intrinsicEarth, List<Intrinsic> intrinsicNamek,
            List<Intrinsic> intrinsicSaiyan, List<HeadAvatar> headAvatars,
            List<BgItem> backgroundItems, List<FlagBag> flagBags, List<NClass> classes,
            List<Shop> shops, List<String> notifications, List<BadgesTaskTemplate> badgeTasks,
            List<BagesTemplate> badges) {

        private static Snapshot from(DataBundle bundle) {
            if (bundle == null) throw new IllegalArgumentException("bundle must not be null");
            return new Snapshot(bundle.revision(), bundle.mapTemplates(), bundle.itemOptionTemplates(),
                    bundle.headFrames(), bundle.imagesByName(), bundle.itemTemplates(),
                    bundle.defaultItemOptions(), bundle.mobTemplates(), bundle.npcTemplates(),
                    bundle.tasks(), bundle.sideTasks(), bundle.clanTasks(), bundle.achievements(),
                    bundle.intrinsics(), bundle.intrinsicEarth(), bundle.intrinsicNamek(),
                    bundle.intrinsicSaiyan(), bundle.headAvatars(), bundle.backgroundItems(),
                    bundle.flagBags(), bundle.classes(), bundle.shops(), bundle.notifications(),
                    bundle.badgeTasks(), bundle.badges());
        }

        @Override
        public MapTemplate[] mapTemplates() { return mapTemplates.clone(); }
    }
}
