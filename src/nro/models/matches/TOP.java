package nro.models.matches;

import lombok.Builder;
import lombok.Data;
import nro.models.player.Player;

@Data
@Builder
public class TOP {

    private String name;
    private byte gender;
    private short head;
    private short body;
    private short leg;
    private long power;
    private long ki;
    private long hp;
    private long sd;
    private byte nv;
    private byte subnv;
    private int sk;
    private int pvp;
    private int nhs;
    private int dicanh;
    private int divdst;
    private int juventus;
    private long lasttime;
    private long time;
    private int level;
    private int cash;
    private int thoivang;
    private int id_player;
    private String info1;
    private String info2;
    private long paramCompare;
    
    public void setId_player(int id_player) {
    this.id_player = id_player;
}

}
