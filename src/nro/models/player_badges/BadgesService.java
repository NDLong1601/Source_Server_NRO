package nro.models.player_badges;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nro.models.player.Player;
import nro.models.network.Message;
import nro.models.services.Service;

public class BadgesService {

    public static final int DEFAULT_BADGE_DURATION_DAYS = 30;
    public static final byte VISIBILITY_COMMAND = -48;

    private BadgesService() {
    }

    public static boolean hasBadge(Player player, int idEffect) {
        if (player == null || player.dataBadges == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        for (BadgesData data : player.dataBadges) {
            if (data != null && data.idBadGes == idEffect && data.timeofUseBadges > now) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gives one title only once, selects it immediately and leaves all list
     * ownership changes in this method. This keeps shop and task rewards from
     * accidentally adding the same object twice.
     */
    public static boolean grantBadge(Player player, int idEffect, int days) {
        if (player == null || idEffect < 0 || BagesTemplate.findBadgesByIdEffect(idEffect) == null) {
            return false;
        }
        if (player.dataBadges == null) {
            player.dataBadges = new ArrayList<>();
        }
        normalize(player);
        if (hasBadge(player, idEffect)) {
            return false;
        }
        for (BadgesData data : player.dataBadges) {
            data.isUse = false;
        }
        player.dataBadges.add(new BadgesData(idEffect, days));
        return true;
    }

    public static boolean turnOnBadges(Player player, int idEffect) {
        if (player == null || player.dataBadges == null) {
            return false;
        }
        normalize(player);
        boolean found = false;
        for (BadgesData data : player.dataBadges) {
            boolean shouldUse = data.idBadGes == idEffect;
            data.isUse = shouldUse;
            found |= shouldUse;
        }
        return found;
    }

    public static int getActiveBadgeId(Player player) {
        if (player == null || player.dataBadges == null) {
            return -1;
        }
        long now = System.currentTimeMillis();
        for (BadgesData data : player.dataBadges) {
            if (data != null && data.isUse && data.timeofUseBadges > now) {
                return data.idBadGes;
            }
        }
        return -1;
    }

    /**
     * Changes only the visual state of the title. It never changes isUse, so
     * all title options continue to be included in the player's stats.
     */
    public static void setVisualHidden(Player player, boolean hidden) {
        if (player == null) {
            return;
        }
        player.hideBadges = hidden;
        sendVisualHiddenState(player);

        int activeBadgeId = getActiveBadgeId(player);
        if (hidden) {
            // Tell all clients in the map to discard the currently displayed banner.
            Service.gI().sendBadgesPlayer(player, 0, -1);
        } else if (activeBadgeId != -1) {
            // Restore the title immediately instead of waiting for the next 10-second refresh.
            Service.gI().sendBadgesPlayer(player, 5, activeBadgeId);
            player.badges.lastTimeSendBadges = System.currentTimeMillis();
        }
    }

    /** Sends the persisted setting to the owner after login and after a change. */
    public static void sendVisualHiddenState(Player player) {
        if (player == null) {
            return;
        }
        Message msg = null;
        try {
            msg = new Message(24);
            msg.writer().writeByte(5);
            msg.writer().writeByte(player.hideBadges ? 1 : 0);
            player.sendMessage(msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    /**
     * Repairs expired, duplicated and legacy title records. The result is
     * persisted on the player's next save, so this also migrates old JSON
     * without a risky bulk rewrite of the player table.
     */
    public static boolean normalize(Player player) {
        if (player == null || player.dataBadges == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Map<Integer, BadgesData> uniqueBadges = new LinkedHashMap<>();
        boolean changed = false;
        for (BadgesData data : player.dataBadges) {
            if (data == null || data.idBadGes < 0 || data.timeofUseBadges <= now) {
                changed = true;
                continue;
            }
            int normalizedId = BagesTemplate.normalizeEffectId(data.idBadGes);
            if (normalizedId != data.idBadGes) {
                data.idBadGes = normalizedId;
                changed = true;
            }
            BadgesData current = uniqueBadges.get(data.idBadGes);
            if (current == null) {
                uniqueBadges.put(data.idBadGes, data);
            } else {
                changed = true;
                if (data.timeofUseBadges > current.timeofUseBadges
                        || (data.timeofUseBadges == current.timeofUseBadges && data.isUse && !current.isUse)) {
                    uniqueBadges.put(data.idBadGes, data);
                }
            }
        }

        BadgesData active = null;
        for (BadgesData data : uniqueBadges.values()) {
            if (!data.isUse) {
                continue;
            }
            if (active == null || data.timeofUseBadges > active.timeofUseBadges) {
                if (active != null) {
                    active.isUse = false;
                    changed = true;
                }
                active = data;
            } else {
                data.isUse = false;
                changed = true;
            }
        }

        if (changed) {
            List<BadgesData> normalizedBadges = new ArrayList<>(uniqueBadges.values());
            player.dataBadges.clear();
            player.dataBadges.addAll(normalizedBadges);
        }
        return changed;
    }

}
