package nro.models.mob;

import nro.models.utils.Util;

public class MobPoint {

    public final Mob mob;
    public int hp;
    public int maxHp;
    public int dame;

    public MobPoint(Mob mob) {
        this.mob = mob;
    }

    public int getHpFull() {
        return maxHp;
    }

    public void setHpFull(int hp) {
        maxHp = hp;
    }

    public int gethp() {
        return hp;
    }

    public void sethp(int hp) {
        if (this.hp < 0) {
            this.hp = 0;
        } else {
            this.hp = hp;
        }
    }

    public int getDameAttack() {
        return this.dame != 0 ? this.dame + Util.nextInt(-(this.dame / 100), (this.dame / 100))
                : this.getHpFull() * Util.nextInt(mob.pDame - 1, mob.pDame + 1) / 100
                + Util.nextInt(-(mob.level * 10), mob.level * 10);
    }
}
