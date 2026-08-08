package nro.models.matches;
import nro.models.consts.ConstPlayer;
import nro.models.interfaces.IPVP;
import nro.models.managers.PVPManager;
import nro.models.player.Player;
import nro.models.services.PlayerService;

public abstract class PVP implements IPVP {

    public TYPE_PVP typePVP;

    public Player p1;
    public Player p2;

    public long lastTimeStart;
    public boolean started;

    public PVP(TYPE_PVP type, Player p1, Player p2) {
        this.typePVP = type;
        this.p1 = p1;
        this.p2 = p2;
        p1.pvp = this;
        p2.pvp = this;
        this.lastTimeStart = System.currentTimeMillis();
        this.start();
        PVPManager.gI().addPVP(this);
    }

    @Override
    public void start() {
        this.started = true;
        this.changeToTypePK();
    }

    protected void changeToTypePK() {
        if (this.p1 != null && this.p2 != null) {
            PlayerService.gI().changeAndSendTypePK(this.p1, ConstPlayer.PK_PVP);
            PlayerService.gI().changeAndSendTypePK(this.p2, ConstPlayer.PK_PVP);
        }
    }

    private void changeToTypeNonPK() {
        if (this.p1 != null && this.p2 != null) {
            PlayerService.gI().changeAndSendTypePK(this.p1, ConstPlayer.NON_PK);
            PlayerService.gI().changeAndSendTypePK(this.p2, ConstPlayer.NON_PK);
        }
    }

    @Override
    public boolean isInPVP(Player pl) {
        return this.p1.equals(pl) || this.p2.equals(pl);
    }

    @Override
    public void lose(Player plLose, TYPE_LOSE_PVP typeLose) {
        if (started) {
            this.finish();
            if (plLose.equals(p1)) {
                this.reward(p2);
            } else {
                this.reward(p1);
            }
            this.sendResult(plLose, typeLose);
            this.dispose();
        }
    }

    @Override
    public void dispose() {
        this.changeToTypeNonPK();
        if (this.p1 != null) {
            this.p1.pvp = null;
            this.p1 = null;
        }
        if (this.p2 != null) {
            this.p2.pvp = null;
            this.p2 = null;
        }
        PVPManager.gI().removePVP(this);
    }
}
