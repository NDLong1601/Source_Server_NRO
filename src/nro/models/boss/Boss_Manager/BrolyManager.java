package nro.models.boss.Boss_Manager;

public class BrolyManager extends BossManager {

    private static BrolyManager instance;

    public static BrolyManager gI() {
        if (instance == null) {
            instance = new BrolyManager();
        }
        return instance;
    }

}
