package nro.models.clan;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Defines mutable clan-owned rows removed on dissolution; audit and owed rewards are retained. */
public final class ClanDissolutionPolicy {

    private static final Set<String> ACTIVE_STATE_TABLES = orderedSet(
            "clan_tree_member",
            "clan_tree",
            "clan_potential",
            "clan_shop_stock",
            "clan_gift_daily",
            "clan_gift_pair",
            "clan_item_storage",
            "clan_active_buff",
            "clan_member_contribution");

    private ClanDissolutionPolicy() {
    }

    public static Set<String> activeStateTables() {
        return ACTIVE_STATE_TABLES;
    }

    private static Set<String> orderedSet(String... values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Collections.addAll(result, values);
        return Collections.unmodifiableSet(result);
    }
}
