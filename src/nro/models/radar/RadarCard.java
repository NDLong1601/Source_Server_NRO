package nro.models.radar;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author By Mr Blue
 * 
 */

public class RadarCard {

    public short Id;
    public short IconId;
    public byte Rank;
    public byte Max;
    public byte Type;
    public short Template;
    public short Head;
    public short Body;
    public short Leg;
    public short Bag;
    public String Name;
    public String Info;
    public List<OptionCard> Options;
    public short Require;
    public short RequireLevel;
    public short AuraId;
    public byte[] Milestones;

    public RadarCard() {
        Id = -1;
        IconId = -1;
        Rank = 0;
        Max = 0;
        Type = 0;
        Template = 1;
        Name = "";
        Info = "";
        Options = new ArrayList<>();
        Require = -1;
        RequireLevel = 0;
        AuraId = -1;
        Milestones = new byte[]{1, 0, 0};
    }

    public byte getRequiredAmountForLevel(int level) {
        if (level <= 0) {
            return 1;
        }
        if (Milestones != null && level < Milestones.length && Milestones[level] > 0) {
            return Milestones[level];
        }
        return Max;
    }

    public byte getNextRequiredAmount(byte currentLevel) {
        int nextLevel = currentLevel < 0 ? 1 : currentLevel + 1;
        return getRequiredAmountForLevel(nextLevel);
    }

    public byte getMaxLevel() {
        byte maxLevel = 2;
        if (Milestones != null) {
            for (int i = 1; i < Milestones.length; i++) {
                if (Milestones[i] > 0) {
                    maxLevel = (byte) i;
                }
            }
        }
        return maxLevel;
    }
}
