package nro.models.boss.Boss_Manager;

public class HalloweenEventManager extends BossManager {

    private static HalloweenEventManager instance;

    public static HalloweenEventManager gI() {
        if (instance == null) {
            instance = new HalloweenEventManager();
        }
        return instance;
    }
}
