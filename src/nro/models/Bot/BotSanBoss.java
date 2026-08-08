package nro.models.Bot;

import java.util.Comparator;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;
import nro.models.consts.ConstPlayer;
import nro.models.boss.Boss;
import nro.models.boss.Boss_Manager.BossManager;
import nro.models.map.service.ChangeMapService;
import nro.models.mob.Mob;
import nro.models.skill.Skill;
import nro.models.map.service.MapService;
import nro.models.services.PlayerService;
import nro.models.services.SkillService;
import nro.models.utils.Util;

public class BotSanBoss {

    public Bot bot;

    public Boss bossAttack;

    public long lastTimeSkill1;

    public BotSanBoss(Bot b) {
        this.bot = b;
    }

    public void update() {
        this.SanBot();
    }

    public boolean isMap(int mapId) {
        return (MapService.gI().isMapDoanhTrai(mapId) || MapService.gI().isMapBlackBallWar(mapId)
                || MapService.gI().isMapBanDoKhoBau(mapId) || MapService.gI().isMapMaBu(mapId)
                || MapService.gI().isMapMaBu(mapId)
                || MapService.gI().isMapConDuongRanDoc(mapId));
    }

    public void GetBoss(int status) {
        if (this.bossAttack == null || this.bossAttack.isDie()) {
            this.bossAttack = BossManager.gI().getBosses().get(new Random().nextInt(BossManager.gI().getBosses().size()));

            boolean bosAction = false;
            if (this.bossAttack != null && this.bossAttack.zone != null && this.bossAttack.zone.map != null) {
                bosAction = !this.bossAttack.isDie()
                        && !this.isMap(this.bossAttack.zone.map.mapId)
                        && !this.bossAttack.zone.isFullPlayer()
                        && this.bossAttack.zone.mobs != null
                        && this.bossAttack.zone.mobs.size() >= 1;
            }

            if (bosAction) {
                ChangeMapService.gI().goToMap(this.bot, this.bossAttack.zone);
                this.bot.zone.load_Me_To_Another(this.bot);
            }

            if (!bosAction && status < 3) {
                this.bossAttack = null;
                this.GetBoss(status + 1);
            } else if (!bosAction) {
                BotManager.gI().bot.remove(this.bot);
                ChangeMapService.gI().exitMap(this.bot);
                this.bossAttack = null;
            }
        }
    }

    public void GetSkil() {
        if (Util.isTrue(50, 100)) {
            this.bot.playerSkill.skillSelect = this.bot.playerSkill.skills.get(0);
        } else {
            this.bot.playerSkill.skillSelect = this.bot.playerSkill.skills.get(1);
        }
        if (this.lastTimeSkill1 < System.currentTimeMillis() - 50000) {
            switch (this.bot.gender) {
                case ConstPlayer.XAYDA:
                    this.bot.useSkill(Skill.BIEN_KHI);
                    break;
                case ConstPlayer.TRAI_DAT:
                    this.bot.useSkill(Skill.QUA_CAU_KENH_KHI);
                    break;
                case ConstPlayer.NAMEC:
                    this.bot.useSkill(Skill.MAKANKOSAPPO);
                    break;
            }
            this.lastTimeSkill1 = System.currentTimeMillis() - new Random().nextInt(150000);
        }
    }

    public void SanBot() {
        this.GetBoss(0);
        this.GetSkil();
        if (this.bossAttack != null && !this.bossAttack.isDie()) {
            if (this.bot.UseLastTimeSkill()) {
                int y = 0;
                int x = 0;
                for (Mob m : this.bot.zone.mobs) {
                    if (y < m.location.y) {
                        y = m.location.y;
                        x = m.location.x;
                    }

                }
                if (this.bot.zone.map.mapId == 72) {
                    y = 312;
                }
                PlayerService.gI().playerMove(this.bot, this.bossAttack.location.x, this.bossAttack.location.y);
                SkillService.gI().useSkill(this.bot, this.bossAttack, null, 0, null);
            }
        }
    }
}
