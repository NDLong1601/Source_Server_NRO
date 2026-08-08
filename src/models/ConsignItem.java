package models;

import nro.models.item.Item.ItemOption;
import java.util.ArrayList;
import java.util.List;

public class ConsignItem {
     public int id;
    public short itemId;
    public int player_sell;
    public byte tab;
    public int goldSell;
    public int gemSell;
    public int quantity;
    public byte isUpTop;
    public List<ItemOption> options = new ArrayList<>();
    public boolean isBuy;

    public ConsignItem(){
    }

    public ConsignItem(int i,short id,int plId,byte t,int gold,int gem,int q,byte isUp,List<ItemOption> op,boolean b){
        this.id = i;
        itemId = id;
        player_sell=plId;
        tab = t;
        goldSell = gold;
        gemSell = gem;
        quantity =q;
        isUpTop = isUp;
        options = op;
        isBuy = b;
    }
}


