package nro.models.services;

import nro.models.radar.Card;
import nro.models.radar.OptionCard;
import nro.models.radar.RadarCard;
import nro.models.player.Player;
import nro.models.network.Message;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author By Mr Blue
 * 
 */

public class RadarService {

    public List<RadarCard> RADAR_TEMPLATE = new ArrayList<>();

    private static RadarService instance;

    public static RadarService gI() {
        if (instance == null) {
            instance = new RadarService();
        }
        return instance;
    }

    public void sendRadar(Player pl, List<Card> cards) {
        try {
            Message m = new Message(127);
            m.writer().writeByte(0);
            m.writer().writeShort(RadarService.gI().RADAR_TEMPLATE.size());
            for (RadarCard radar : RadarService.gI().RADAR_TEMPLATE) {
                Card card = cards.stream().filter(c -> c.Id == radar.Id).findFirst().orElse(null);
                if (card == null) {
                    card = new Card(radar.Max, radar.Options);
                }
                m.writer().writeShort(radar.Id);
                m.writer().writeShort(radar.IconId);
                m.writer().writeByte(radar.Rank);
                m.writer().writeByte(card.Amount);  //amount
                m.writer().writeByte(card.MaxAmount);  //max_amount
                m.writer().writeByte(radar.Type);  //type 0: monster, 1: charpart
                switch (radar.Type) {
                    case 0:
                        m.writer().writeShort(radar.Template); //Monster
                        break;
                    case 1:
                        m.writer().writeShort(radar.Head); //Head
                        m.writer().writeShort(radar.Body); //Body
                        m.writer().writeShort(radar.Leg); //Leg
                        m.writer().writeShort(radar.Bag); //bag
                        break;
                }
                m.writer().writeUTF(radar.Name);  //name
                m.writer().writeUTF(radar.Info);  //info
                m.writer().writeByte(card.Level);  //Level
                m.writer().writeByte(card.Used);  //use
                m.writer().writeByte(radar.Options.size());  //option radar
                for (OptionCard option : radar.Options) {
                    m.writer().writeByte(option.id);  //id
                    m.writer().writeShort(option.param);  //param
                    m.writer().writeByte(option.active);  //ActiveCard
                }
            }
            m.writer().flush();
            pl.sendMessage(m);
            m.cleanup();
        } catch (Exception e) {
        }
    }

    public void Radar1(Player pl, short id, int use) {
        try {
            Message message = new Message(127);
            message.writer().writeByte(1);
            message.writer().writeShort(id);
            message.writer().writeByte(use);
            message.writer().flush();
            pl.sendMessage(message);
            message.cleanup();
        } catch (Exception e) {
        }
    }

    public void RadarSetLevel(Player pl, int id, int level) {
        try {
            Message message = new Message(127);
            message.writer().writeByte(2);
            message.writer().writeShort(id);
            message.writer().writeByte(level);
            message.writer().flush();
            pl.sendMessage(message);
            message.cleanup();
        } catch (Exception e) {
        }
    }

    public void RadarSetAmount(Player pl, int id, int amount, int max_amount) {
        try {
            Message message = new Message(127);
            message.writer().writeByte(3);
            message.writer().writeShort(id);
            message.writer().writeByte(amount);
            message.writer().writeByte(max_amount);
            message.writer().flush();
            pl.sendMessage(message);
            message.cleanup();
        } catch (Exception e) {
        }
    }

    public void sendAura(Player pl, int id_Aura, int id_Eff_Set_Item) {
        try {
            Message message = new Message(127);
            message.writer().writeByte(4);
            message.writer().writeInt((int) pl.id);
            message.writer().writeShort(id_Aura);
            message.writer().writeByte(id_Eff_Set_Item);
            Service.gI().sendMessAllPlayer(message);
            message.cleanup();
        } catch (Exception e) {
        }
    }
}
