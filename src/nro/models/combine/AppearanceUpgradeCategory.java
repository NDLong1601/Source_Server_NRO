package nro.models.combine;

import java.util.HashSet;
import java.util.Set;
import nro.models.item.Item;
import nro.models.services.InventoryService;

/**
 * Phân loại nhóm ngoại trang nâng cấp.
 */
public enum AppearanceUpgradeCategory {
    COSTUME("costume", "Cải trang", 2052),
    FOLLOW_PET("followPet", "Linh thú/Pet", 2053),
    BACK("back", "Cánh/Đeo lưng", 2054),
    MOUNT("mount", "Ván bay/Thú cưỡi", 2055);

    private final String configKey;
    private final String displayName;
    private final int defaultStoneId;

    AppearanceUpgradeCategory(String configKey, String displayName, int defaultStoneId) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.defaultStoneId = defaultStoneId;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getStoneId() {
        return CombineConfig.getInt("appearance." + configKey + ".stoneId", defaultStoneId);
    }

    public boolean isEnabled() {
        return CombineConfig.getBoolean("appearance." + configKey + ".enabled", true);
    }

    public boolean isExcluded(Item item) {
        if (item == null || item.template == null) {
            return true;
        }
        int[] excludes = CombineConfig.getIntArray("appearance." + configKey + ".excludeTemplateIds");
        for (int id : excludes) {
            if (id == item.template.id) {
                return true;
            }
        }
        return false;
    }

    public boolean matches(Item item) {
        if (item == null || !item.isNotNullItem() || item.template == null) {
            return false;
        }
        if (!isEnabled() || isExcluded(item)) {
            return false;
        }
        switch (this) {
            case COSTUME:
                return item.template.type == 5;
            case MOUNT:
                if (item.template.type == 23 || item.template.type == 24) {
                    return true;
                }
                String mountName = item.template.name != null ? item.template.name.toLowerCase() : "";
                return mountName.contains("ván bay") || mountName.contains("thú cưỡi") || mountName.contains("cân đẩu vân");
            case BACK:
                return item.template.type == 11;
            case FOLLOW_PET:
                return isFollowPetItem(item);
            default:
                return false;
        }
    }

    public static boolean isFollowPetItem(Item item) {
        if (item == null || !item.isNotNullItem() || item.template == null) {
            return false;
        }
        int tid = item.template.id;
        // Loại trừ nguyên liệu nâng cấp, rương, khiên, bảo hộ
        if (tid >= 2052 && tid <= 2061) {
            return false;
        }
        if (tid == 987 || tid == 1819 || tid == 1820) {
            return false;
        }

        int[] petIds = CombineConfig.getIntArray("appearance.followPet.templateIds");
        if (petIds != null && petIds.length > 0) {
            for (int id : petIds) {
                if (id == tid) {
                    return true;
                }
            }
        }

        String name = item.template.name != null ? item.template.name.toLowerCase() : "";
        if (name.contains("pet") || name.contains("linh thú")) {
            return true;
        }

        if (item.template.type == 27 || item.template.type == 28) {
            if (name.startsWith("hộp quà") || name.startsWith("rương")
                    || name.startsWith("mảnh") || name.startsWith("vé")
                    || name.startsWith("cỏ ") || name.startsWith("sao ")
                    || name.startsWith("khiên ") || name.startsWith("sách giáo khoa")
                    || name.startsWith("bùa") || name.startsWith("bông tai")
                    || name.startsWith("thẻ")) {
                return false;
            }
            return true;
        }
        return false;
    }

    public static AppearanceUpgradeCategory categorize(Item item) {
        if (item == null || !item.isNotNullItem()) {
            return null;
        }
        for (AppearanceUpgradeCategory cat : values()) {
            if (cat.matches(item)) {
                return cat;
            }
        }
        return null;
    }

    public static boolean isAnyUpgradeStone(Item item) {
        if (item == null || !item.isNotNullItem() || item.template == null) {
            return false;
        }
        for (AppearanceUpgradeCategory cat : values()) {
            if (item.template.id == cat.getStoneId()) {
                return true;
            }
        }
        return false;
    }
}
