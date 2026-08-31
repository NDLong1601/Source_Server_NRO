import java.util.ArrayList;
import java.util.List;
import nro.models.skill.SuperGhostFormation;

public final class SuperGhostFormationTest {
    static final class Victim implements SuperGhostFormation.Target {
        boolean alive = true;
        int x = 320, y = 180, hits, dieAfter = 2;
        public boolean valid() { return alive; }
        public int x() { return x; }
        public int y() { return y; }
    }
    static final class Run implements SuperGhostFormation.Listener {
        long now;
        final List<Integer> events = new ArrayList<>();
        final List<Integer> hitIds = new ArrayList<>();
        final SuperGhostFormation f;
        Run(int level, int count) { f = new SuperGhostFormation(level, count, 0, 120, 200, () -> 2, this); }
        public void event(int e, int index, long now) { events.add(e); }
        public void hit(SuperGhostFormation.Ghost g, SuperGhostFormation.Target t) {
            check(!hitIds.contains(g.index), "no duplicate damage");
            hitIds.add(g.index);
            Victim v = (Victim)t;
            if (++v.hits >= v.dieAfter) v.alive = false;
        }
        void advance(long until) {
            while (now < until) {
                now += 50;
                f.tick(now, 120, 200);
                int flying = 0;
                for (var g : f.ghosts) if (g.state == SuperGhostFormation.FLYING) flying++;
                check(flying <= 1, "only one projectile in flight");
            }
        }
    }
    static int assertions;
    static void check(boolean result, String label) {
        assertions++;
        if (!result) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        for (int level = 1; level <= 7; level++) {
            Run r = new Run(level, level);
            check(r.f.lifetimeMs == 20_000 + 5_000 * (level - 1), "level lifetime");
            check(r.f.summonMs == 540 + 55 * (level - 1), "all three summon frames per ghost");
            check(r.f.remaining() == level, "level ghost count");
            int total = 0;
            for (var g : r.f.ghosts) total += g.damageShare;
            check(total == 100, "initial damage budget");
            r.f.tick(r.f.expiresAt - 1, 120, 200);
            check(!r.f.ended, "alive just before expiry");
            r.f.tick(r.f.expiresAt, 120, 200);
            check(r.f.ended, "ends exactly at expiry, including >32767ms");
        }
        Run r = new Run(7,7);
        Victim first = new Victim();
        check(r.f.command(first,0), "queue during summon");
        check(!r.f.command(first,0), "reject spam while ordered");
        r.advance(8_000);
        check(first.hits == 2 && r.f.remaining() == 5 && !r.f.ended, "7 -> kill with 2 -> retain 5");
        check(r.f.target == null, "stop automatically after death");
        for (int i=2;i<7;i++) {
            check(r.f.ghosts[i].state == SuperGhostFormation.ORBITING, "unused ghosts keep orbiting");
            check(r.f.ghosts[i].damageShare == 14, "remaining damage never renormalized");
        }
        Victim second = new Victim(); second.dieAfter=99; second.x=-100;
        check(r.f.command(second,r.now), "retarget remaining ghosts to the left");
        r.advance(20_000);
        check(second.hits==5 && r.f.remaining()==0 && r.f.ended, "consume remainder on second victim");
        check(r.hitIds.size()==7, "seven impacts total");
        check(r.f.expiresAt==50_870, "commands never extend expiry");
        for (var g:r.f.ghosts) check(g.variant==2,"server chooses flight variant");

        Run recalled = new Run(4,4);
        Victim fleeing=new Victim(); fleeing.x=650;
        recalled.f.command(fleeing,0); recalled.advance(900);
        fleeing.alive=false; recalled.advance(4_000);
        check(recalled.hitIds.isEmpty() && recalled.f.remaining()==4, "target dies before collision: no consumption");
        check(recalled.events.contains(SuperGhostFormation.RECALL), "recall event sent");
        for(var g:recalled.f.ghosts) check(g.state==SuperGhostFormation.ORBITING,"recall ends in orbit");
        check(recalled.f.command(new Victim(),recalled.now),"new order accepted after recall");
        recalled.f.end(recalled.now); recalled.advance(9_000);
        check(recalled.hitIds.isEmpty(),"cancel/death/map leave prevents delayed damage");

        Run timeout=new Run(2,2); Victim fast=new Victim(); fast.x=20_000;
        timeout.f.command(fast,0); timeout.advance(8_000);
        check(timeout.f.remaining()==2 && timeout.f.target==null,"unreachable target recalled without spending");
        Run independentA=new Run(1,1), independentB=new Run(7,7);
        independentA.f.end(1);
        check(!independentB.f.ended && independentB.f.remaining()==7,"formations isolated between players");
        System.out.println("PASS SuperGhostFormation: " + assertions + " assertions");
    }
}
