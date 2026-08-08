package nro.models.boss.Boss_Manager;

/**
 *
 * @author By Mr Blue
 */
public class AnTromManager extends BossManager {

    private static AnTromManager instance;

    public static AnTromManager gI() {
        if (instance == null) {
            instance = new AnTromManager();
        }
        return instance;
    }

}