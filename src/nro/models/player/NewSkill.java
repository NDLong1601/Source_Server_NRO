package nro.models.player;

import nro.models.item.Item;
import nro.models.mob.Mob;
import nro.models.skill.Skill;
import nro.models.services.SkillService;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 *
 * @author By Mr Blue
 *
 */
public class NewSkill {

    public static final int TIME_GONG = 2000;
    public static final int TIME_END_24_25 = 3000;
    public static final int TIME_END_26 = 11000;

    private Player player;

    public NewSkill(Player player) {
        this.player = player;
        this.playersTaget = new ArrayList<>();
        this.mobsTaget = new ArrayList<>();
    }

    public Skill skillSelect;

    public byte dir;

    public short _xPlayer;

    public short _yPlayer;

    public short _xObjTaget;

    public short _yObjTaget;

    public List<Player> playersTaget;

    public List<Mob> mobsTaget;

    public boolean isStartSkillSpecial;

    public byte stepSkillSpecial;

    public byte typePaint;

    public byte typeItem;

    public long lastTimeSkillSpecial;

    public byte getTypePaint() {
        int index = player.isPet ? 8 : 10;
        if (player.inventory.itemsBody.size() > index) {
            Item item = player.inventory.itemsBody.get(index);
            if (item != null && item.isNotNullItem()) {
                int itemId = item.template.id;
                if (itemId == 1044 || itemId == 1211 || itemId == 1212) {
                    return 2;
                }
                if (itemId == 1278 || itemId == 1279 || itemId == 1280) {
                    return 3;
                }
            }
        }
        return 0;
    }

    public byte getTypeItem() {
        int index = player.isPet ? 8 : 10;
        if (player.inventory.itemsBody.size() > index) {
            Item item = player.inventory.itemsBody.get(index);
            if (item != null && item.isNotNullItem()) {
                if (item.template.id == 1279 && player.gender == 1) {
                    return 2;
                }
                return 1;
            }
        }
        return 0;
    }

    private void update() {
        if (this.isStartSkillSpecial = true) {
            SkillService.gI().updateSkillSpecial(player);
        }
    }

    public void setSkillSpecial(byte dir, short _xPlayer, short _yPlayer, short _xObjTaget, short _yObjTaget) {
        if (player.itemTime != null && player.itemTime.isUseNCD) {
            typeItem = 2;
        } else {
            typeItem = 0;
        }
        this.skillSelect = this.player.playerSkill.skillSelect;
        if (this.player.isPl() && skillSelect.currLevel < 1000) {
            skillSelect.currLevel++;
            SkillService.gI().sendCurrLevelSpecial(player, skillSelect);
        }
        this.dir = dir;
        this._xPlayer = _xPlayer;
        this._yPlayer = _yPlayer;

        int length = _xObjTaget - _xPlayer;
        int dx = dir * (skillSelect.point + 400);
        if (skillSelect.template.id != Skill.MA_PHONG_BA) {
            if (Math.abs(dx) < Math.abs(length) || Math.abs(length) < 100) {
                this._xObjTaget = (short) dx;
            } else {
                this._xObjTaget = (short) length;
            }
        } else {
            this._xObjTaget = 75;
        }
        this._xObjTaget = (short) Math.abs(this._xObjTaget);
        this._yObjTaget = (short) Math.abs(_yObjTaget);
        this.isStartSkillSpecial = true;
        this.stepSkillSpecial = 0;
        this.lastTimeSkillSpecial = System.currentTimeMillis();
        this.start(250);
    }

    public void sonPhiPhai() {
        if (player.isAdmin()) {
            typePaint = -1;
        }
    }

    public void closeSkillSpecial() {
        this.isStartSkillSpecial = false;
        this.stepSkillSpecial = 0;
        this.playersTaget.clear();
        this.mobsTaget.clear();
        this.close();
    }

    private Timer timer;
    private TimerTask timerTask;
    private boolean isActive = false;

    private void close() {
        try {
            this.isActive = false;
            this.timer.cancel();
            this.timerTask.cancel();
            this.timer = null;
            this.timerTask = null;
        } catch (Exception e) {
            this.timer = null;
            this.timerTask = null;
        }
    }

    public void start(int leep) {
        if (this.isActive == false) {
            this.isActive = true;
            this.timer = new Timer();
            this.timerTask = new TimerTask() {
                @Override
                public void run() {
                    if (player == null || player.newSkill == null) {
                        close();
                        return;
                    }
                    NewSkill.this.update();
                }
            };
            this.timer.schedule(timerTask, leep, leep);
        }
    }

    public void dispose() {
        this.player = null;
        this.skillSelect = null;
    }

}
