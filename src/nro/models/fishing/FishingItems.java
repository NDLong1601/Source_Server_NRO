package nro.models.fishing;

/**
 * Contiguous item-template IDs reserved for the Fishing Event.
 *
 * <p>These IDs are deliberately kept in one place: {@code ItemService} indexes
 * templates by ID, so changing an ID after release would corrupt inventories.</p>
 */
public final class FishingItems {

    private FishingItems() {
    }

    /**
     * Per-item marker persisted on the fishing bait or tackle that the player
     * has selected. The patched local client renders this option as a glowing
     * border around the inventory slot; its option-template text also makes
     * the state visible in the normal item tooltip.
     */
    public static final int EQUIPPED_MARKER_OPTION_ID = 251;
    public static final int EQUIPPED_MARKER_EFFECT = 1;

    public static final short ROD_CRUDE = 2083;
    public static final short ROD_COMMON = 2084;
    public static final short ROD_ADVANCED = 2085;
    public static final short ROD_ROYAL = 2086;
    public static final short ROD_SEA_KING = 2087;

    public static final short BAIT_WORM = 2088;
    public static final short BAIT_DOUGH = 2089;
    public static final short BAIT_LARVA = 2090;
    public static final short BAIT_SHRIMP = 2091;
    public static final short BAIT_BLUE_FISH = 2092;
    public static final short BAIT_CRYSTAL = 2093;
    public static final short BAIT_GOLDEN_CARP = 2094;

    public static final short LINE_MONOFILAMENT = 2095;
    public static final short LINE_STEEL = 2096;
    public static final short LINE_DRAGON_SCALE = 2097;

    public static final short FLOAT_WOOD = 2098;
    public static final short FLOAT_JADE = 2099;
    public static final short FLOAT_SEA_GOD = 2100;

    public static final short REEL_OLD = 2101;
    public static final short REEL_ADVANCED = 2102;
    public static final short REEL_SEA_KING = 2103;

    public static final short HOOK_STEEL = 2104;
    public static final short HOOK_JADE = 2105;
    public static final short HOOK_GOLDEN = 2106;

    public static final short REPAIR_KIT = 2107;
    public static final short LUCKY_CHARM = 2108;
    public static final short FISH_BASKET = 2109;
    public static final short BAIT_BOX = 2110;
    public static final short FISHER_BADGE = 2111;
    public static final short FISH_FINDER = 2112;
    public static final short FISHER_CHEST = 2113;
    public static final short OCEAN_CRATE = 2114;
    public static final short FISHER_COIN = 2115;

    public static final short JUNK_STONE = 2116;
    public static final short JUNK_TORN_NET = 2117;
    public static final short JUNK_SEAWEED = 2118;
    public static final short JUNK_CORAL = 2119;
    public static final short JUNK_SCRAP_METAL = 2120;
    public static final short JUNK_LARGE_TRASH_BAG = 2121;
    public static final short JUNK_SMALL_TRASH_BAG = 2122;
    public static final short JUNK_BANANA_PEEL = 2123;

    public static final short FISH_SILVER = 2124;
    public static final short FISH_ROCK_PERCH = 2125;
    public static final short FISH_GOLD_CARP = 2126;
    public static final short FISH_PREDATOR_SNAKEHEAD = 2127;
    public static final short FISH_GIANT_CATFISH = 2128;
    public static final short FISH_SILVER_SALMON = 2129;
    public static final short FISH_ARMORED_STURGEON = 2130;
    public static final short FISH_TUNA = 2131;
    public static final short FISH_BLUE_SWORD = 2132;
    public static final short FISH_WHITE_SHARK = 2133;
    public static final short FISH_HAMMERHEAD = 2134;
    public static final short FISH_WHALE_SHARK = 2135;
    public static final short FISH_DEEP_SEA_DEMON = 2136;
    public static final short FISH_JADE_SEA_DRAGON = 2137;
    public static final short FISH_SEA_GOD_DRAGON = 2138;

    public static final short GIANT_FISH_FIRST = 2139;
    public static final short GIANT_FISH_LAST = 2153;

    public static boolean isRod(int itemId) {
        return itemId >= ROD_CRUDE && itemId <= ROD_SEA_KING;
    }

    public static boolean isBait(int itemId) {
        return itemId >= BAIT_WORM && itemId <= BAIT_GOLDEN_CARP;
    }

    public static boolean isLine(int itemId) {
        return itemId >= LINE_MONOFILAMENT && itemId <= LINE_DRAGON_SCALE;
    }

    public static boolean isFloat(int itemId) {
        return itemId >= FLOAT_WOOD && itemId <= FLOAT_SEA_GOD;
    }

    public static boolean isReel(int itemId) {
        return itemId >= REEL_OLD && itemId <= REEL_SEA_KING;
    }

    public static boolean isHook(int itemId) {
        return itemId >= HOOK_STEEL && itemId <= HOOK_GOLDEN;
    }

    /**
     * Returns true only when two fishing items occupy the same selectable
     * equipment slot. A bait replaces bait only; a reel never clears a hook,
     * for example.
     */
    public static boolean isSameEquipmentSlot(int firstItemId, int secondItemId) {
        return (isBait(firstItemId) && isBait(secondItemId))
                || (isLine(firstItemId) && isLine(secondItemId))
                || (isFloat(firstItemId) && isFloat(secondItemId))
                || (isReel(firstItemId) && isReel(secondItemId))
                || (isHook(firstItemId) && isHook(secondItemId));
    }

    public static boolean isFish(int itemId) {
        return itemId >= FISH_SILVER && itemId <= GIANT_FISH_LAST;
    }

    public static short giantFishIdFor(short standardFishId) {
        if (standardFishId < FISH_SILVER || standardFishId > FISH_SEA_GOD_DRAGON) {
            throw new IllegalArgumentException("Not a standard fishing item: " + standardFishId);
        }
        return (short) (GIANT_FISH_FIRST + standardFishId - FISH_SILVER);
    }

    public static boolean isGiantFish(int itemId) {
        return itemId >= GIANT_FISH_FIRST && itemId <= GIANT_FISH_LAST;
    }
}
